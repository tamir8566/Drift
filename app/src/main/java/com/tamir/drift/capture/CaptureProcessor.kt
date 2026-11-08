package com.tamir.drift.capture

import android.util.Log
import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.db.RawCaptureEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.repo.CategoryRepository
import com.tamir.drift.data.repo.Money
import com.tamir.drift.data.repo.toSummaries
import com.tamir.drift.domain.dedupe.DuplicateDetector
import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.ParseStatus
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.UserPattern
import com.tamir.drift.domain.parser.TransactionTextParser
import com.tamir.drift.notify.BudgetAlertChecker
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * One entry point for every captured text (Google Pay notification or max SMS):
 * raw log → parse (user patterns first) → duplicate check (±10 min) → classify → insert → budget alerts.
 * Never throws; failures are logged to the raw-capture debug log.
 */
class CaptureProcessor(
    private val db: DriftDatabase,
    private val categories: CategoryRepository,
    private val alerts: BudgetAlertChecker,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val dedupe = DuplicateDetector()

    sealed class Outcome {
        data class Inserted(val id: Long, val unparsed: Boolean) : Outcome()
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
                is Outcome.Inserted -> outcome.id to (if (outcome.unparsed) "UNPARSED" else "PARSED")
                is Outcome.Duplicate -> outcome.existingId to "DUPLICATE"
                is Outcome.Failed -> null to "FAILED: ${outcome.reason}"
            }
            markResult(rawId, result, txId)
            outcome
        } catch (e: Exception) {
            Log.e(TAG, "capture failed", e)
            markResult(rawId, "FAILED: ${e.message}", null)
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

        if (parsed.status == ParseStatus.UNPARSED || parsed.amount == null || parsed.amount <= 0.0) {
            val id = db.transactions().insert(
                TransactionEntity(
                    timestamp = capturedAt,
                    amountFull = 0,
                    amountMine = 0,
                    merchant = "",
                    categoryId = CategoryKeys.OTHER,
                    source = source.name,
                    isUnparsed = true,
                    rawText = text,
                ),
            )
            return Outcome.Inserted(id, unparsed = true)
        }

        val amountAgorot = Money.toAgorot(parsed.amount)
        val timestamp = resolveTimestamp(parsed.date?.resolve(Instant.ofEpochMilli(capturedAt).atZone(zone).toLocalDate()), capturedAt)

        // Duplicate: same amount within ±10 min, from any non-manual source.
        val window = DuplicateDetector.DEFAULT_WINDOW_MINUTES * 60_000L
        val around = db.transactions().findSameAmountAround(amountAgorot, capturedAt - window, capturedAt + window)
        val candidate = DuplicateDetector.Candidate(parsed.amount, capturedAt, source, parsed.merchant)
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
        val categoryId = categories.classifier().classify(merchant)
        val id = db.transactions().insert(
            TransactionEntity(
                timestamp = timestamp,
                amountFull = amountAgorot,
                amountMine = amountAgorot,
                merchant = merchant,
                categoryId = categoryId,
                source = source.name,
                rawText = text,
                cardLast4 = parsed.last4,
            ),
        )
        alerts.checkAndNotify()
        return Outcome.Inserted(id, unparsed = false)
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
    }
}
