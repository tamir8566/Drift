package com.tamir.drift.capture

import android.util.Log
import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.db.RawCaptureEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.repo.CategoryRepository
import com.tamir.drift.data.repo.Money
import com.tamir.drift.data.repo.toSummaries
import com.tamir.drift.data.settings.SettingsStore
import com.tamir.drift.domain.dedupe.DuplicateDetector
import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.ParseStatus
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnKind
import com.tamir.drift.domain.model.UserPattern
import com.tamir.drift.domain.money.Currencies
import com.tamir.drift.domain.parser.TransactionTextParser
import com.tamir.drift.domain.refund.RefundMatcher
import com.tamir.drift.notify.BudgetAlertChecker
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * One entry point for every captured text (Google Pay notification or max SMS):
 * raw log → parse (user patterns first) → duplicate check (±10 min) → classify → insert → budget alerts.
 * A refund text becomes a refund (it inherits the category and share of the purchase it matches);
 * a foreign amount becomes a shekel estimate from the settings' rates table, with the original
 * amount kept beside it. Never throws; failures are logged to the raw-capture debug log.
 */
class CaptureProcessor(
    private val db: DriftDatabase,
    private val categories: CategoryRepository,
    private val alerts: BudgetAlertChecker,
    private val settings: SettingsStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Called after a captured text was stored as a new row (parsed or not); never for a duplicate. */
    private val onCaptured: () -> Unit = {},
) {
    private val dedupe = DuplicateDetector()

    sealed class Outcome {
        data class Inserted(val id: Long, val unparsed: Boolean, val refund: Boolean = false) : Outcome()
        data class Duplicate(val existingId: Long) : Outcome()
        data class Failed(val reason: String) : Outcome()
    }

    suspend fun process(source: TransactionSource, senderOrPackage: String, text: String, capturedAt: Long): Outcome {
        val rawId = db.rawCaptures().insert(
            RawCaptureEntity(source = source.name, senderOrPackage = senderOrPackage, text = text, capturedAt = capturedAt),
        )
        db.rawCaptures().prune(RAW_LOG_SIZE)

        return try {
            val outcome = handle(source, text, capturedAt)
            val (txId, result) = when (outcome) {
                is Outcome.Inserted -> outcome.id to when {
                    outcome.unparsed -> RESULT_UNPARSED
                    outcome.refund -> RESULT_REFUND
                    else -> RESULT_PARSED
                }
                is Outcome.Duplicate -> outcome.existingId to RESULT_DUPLICATE
                is Outcome.Failed -> null to "$RESULT_FAILED: ${outcome.reason}"
            }
            markResult(rawId, result, txId)
            if (outcome is Outcome.Inserted) runCatching { onCaptured() }
            outcome
        } catch (e: Exception) {
            Log.e(TAG, "capture failed", e)
            markResult(rawId, "$RESULT_FAILED: ${e.message}", null)
            Outcome.Failed(e.message ?: "error")
        }
    }

    private suspend fun markResult(rawId: Long, result: String, txId: Long?) {
        db.rawCaptures().getById(rawId)?.let { db.rawCaptures().update(it.copy(result = result, transactionId = txId)) }
    }

    private suspend fun handle(source: TransactionSource, text: String, capturedAt: Long): Outcome {
        val patterns = db.parsePatterns().getAll().map {
            UserPattern(it.id, it.amountRegex, it.source?.let { s -> runCatching { TransactionSource.valueOf(s) }.getOrNull() })
        }
        val parsed = TransactionTextParser(patterns).parse(text, source)
        // A text that speaks of a credit / cancellation is a refund — never stored as an expense,
        // also when the rest of it could not be parsed.
        val kind = parsed.kind
        val foreign = parsed.isForeign
        val parsedAmount = parsed.amount?.takeIf { parsed.status != ParseStatus.UNPARSED && it > 0.0 }
        // A foreign amount is never read as shekels: it becomes an estimate from the rates table.
        val amountAgorot: Long? = parsedAmount?.let { a ->
            if (foreign) settings.current().fxRates.estimateAgorot(a, parsed.currency) else Money.toAgorot(a)
        }
        val origMinor = if (foreign) parsedAmount?.let { Currencies.toMinor(it) } else null
        val origCurrency = if (foreign && origMinor != null) parsed.currency else null

        if (amountAgorot == null || amountAgorot <= 0L) {
            // No amount (or a foreign amount without a usable rate): the user completes it by hand.
            val id = db.transactions().insert(
                TransactionEntity(
                    timestamp = capturedAt,
                    amountFull = 0,
                    amountMine = 0,
                    merchant = if (origMinor != null) parsed.merchant.orEmpty() else "",
                    categoryId = CategoryKeys.OTHER,
                    source = source.name,
                    isUnparsed = true,
                    rawText = text,
                    kind = kind.name,
                    origAmountMinor = origMinor,
                    origCurrency = origCurrency,
                ),
            )
            return Outcome.Inserted(id, unparsed = true)
        }

        val amountShekels = Money.toShekels(amountAgorot)
        val timestamp = resolveTimestamp(parsed.date?.resolve(Instant.ofEpochMilli(capturedAt).atZone(zone).toLocalDate()), capturedAt)

        // Duplicate: same amount within ±10 min, from any non-manual source, of the same kind.
        val window = DuplicateDetector.DEFAULT_WINDOW_MINUTES * 60_000L
        val around = db.transactions().findSameAmountAround(amountAgorot, capturedAt - window, capturedAt + window)
        val candidate = DuplicateDetector.Candidate(amountShekels, capturedAt, source, parsed.merchant, kind)
        dedupe.findDuplicate(candidate, around.toSummaries())?.let { dup ->
            val existing = db.transactions().getById(dup.id)
            if (existing != null) {
                val merged = existing.copy(
                    rawText = listOfNotNull(existing.rawText, text).joinToString("\n\n---\n\n"),
                    merchant = existing.merchant.ifBlank { parsed.merchant.orEmpty() },
                    cardLast4 = existing.cardLast4 ?: parsed.last4,
                )
                val reclassified = if (existing.merchant.isBlank() && merged.merchant.isNotBlank()) {
                    merged.copy(categoryId = categories.classifier().classify(merged.merchant))
                } else merged
                db.transactions().update(reclassified)
                return Outcome.Duplicate(existing.id)
            }
        }

        val merchant = parsed.merchant.orEmpty()
        var categoryId = categories.classifier().classify(merchant)
        var mineAgorot: Long = amountAgorot
        var shared = false
        if (kind == TxnKind.REFUND) {
            // A refund follows the purchase it belongs to: its category and its "with friends" share.
            val dayMillis = 24L * 60 * 60 * 1000
            val recent = db.transactions().getBetween(timestamp - (RefundMatcher.WINDOW_DAYS + 1) * dayMillis, timestamp + 2 * dayMillis)
            RefundMatcher.findOriginal(merchant, amountShekels, timestamp, recent.toSummaries())?.let { original ->
                categoryId = original.categoryKey
                mineAgorot = Money.toAgorot(RefundMatcher.myShareOf(amountShekels, original)).coerceIn(0L, amountAgorot)
                shared = original.isShared
            }
        }
        val id = db.transactions().insert(
            TransactionEntity(
                timestamp = timestamp,
                amountFull = amountAgorot,
                amountMine = mineAgorot,
                isShared = shared,
                merchant = merchant,
                categoryId = categoryId,
                source = source.name,
                rawText = text,
                cardLast4 = parsed.last4,
                kind = kind.name,
                origAmountMinor = origMinor,
                origCurrency = origCurrency,
                isEstimate = foreign,
            ),
        )
        alerts.checkAndNotify()
        return Outcome.Inserted(id, unparsed = false, refund = kind == TxnKind.REFUND)
    }

    /** Uses the date from the text when present (keeping the capture's clock time), else the capture time. */
    private fun resolveTimestamp(date: java.time.LocalDate?, capturedAt: Long): Long {
        if (date == null) return capturedAt
        val captured = Instant.ofEpochMilli(capturedAt).atZone(zone)
        if (captured.toLocalDate() == date) return capturedAt
        val time: LocalTime = captured.toLocalTime()
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    companion object {
        private const val TAG = "Drift.Capture"
        const val RAW_LOG_SIZE = 20

        /** Outcome codes stored in the raw-capture log (the debug screen shows Hebrew labels for them). */
        const val RESULT_PARSED = "PARSED"
        const val RESULT_REFUND = "REFUND"
        const val RESULT_UNPARSED = "UNPARSED"
        const val RESULT_DUPLICATE = "DUPLICATE"
        const val RESULT_FAILED = "FAILED"
    }
}
