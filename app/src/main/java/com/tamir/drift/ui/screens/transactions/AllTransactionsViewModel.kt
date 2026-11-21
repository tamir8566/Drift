package com.tamir.drift.ui.screens.transactions

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamir.drift.R
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.db.netMineAgorot
import com.tamir.drift.data.db.txnKind
import com.tamir.drift.data.repo.toSummary
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.export.CsvBuilder
import com.tamir.drift.domain.model.TxnKind
import com.tamir.drift.domain.search.TxSearch
import com.tamir.drift.domain.time.MonthPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** [total]: the day's net spend, agorot — my share of expenses minus refunds; transfers are not counted. */
data class DayGroup(val date: LocalDate, val items: List<TransactionEntity>, val total: Long)

/** [unparsedOnly]: show only what could not be parsed (opened from the home attention row). */
data class TxFilter(val category: String? = null, val month: YearMonth? = null, val query: String = "", val unparsedOnly: Boolean = false)

data class AllTxUiState(
    val filter: TxFilter = TxFilter(),
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val categoryList: List<CategoryEntity> = emptyList(),
    val availableMonths: List<YearMonth> = emptyList(),
    val unparsed: List<TransactionEntity> = emptyList(),
    val groups: List<DayGroup> = emptyList(),
    val count: Int = 0,
    /**
     * The line above the list for whatever is filtered: how many expenses, their net total (my
     * share, refunds subtracted, "לא נספר" left out) and the average per expense.
     */
    val summary: TxSearch.Summary = TxSearch.Summary(0, 0.0),
    /** The filtered rows include some from before the tracking start (the line says so). */
    val includesBeforeStart: Boolean = false,
)

class AllTransactionsViewModel(
    private val graph: AppGraph,
    initialCategory: String?,
    initialMonth: String?,
    initialUnparsedOnly: Boolean = false,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val filter = MutableStateFlow(
        TxFilter(
            category = initialCategory,
            month = initialMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
            unparsedOnly = initialUnparsedOnly,
        ),
    )

    val state: StateFlow<AllTxUiState> = combine(graph.transactions.all, graph.categories.categories, filter, graph.settings.settings) { txs, cats, f, settings ->
        val months = txs.map { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }.distinct().sortedDescending()
        val monthPeriod = f.month?.let { MonthPeriod(it, zone) }
        // Free text over merchant / note / raw text; a number also finds amounts ("214" → 214 ₪).
        val filtered = txs.filter { t ->
            (f.category == null || t.categoryId == f.category) &&
                (monthPeriod == null || monthPeriod.contains(t.timestamp)) &&
                TxSearch.matches(f.query, t.merchant, t.note, t.rawText, t.amountFull, t.amountMine)
        }
        val unparsed = filtered.filter { it.isUnparsed }.sortedByDescending { it.timestamp }
        val parsed = if (f.unparsedOnly) emptyList() else filtered.filter { !it.isUnparsed }
        val groups = parsed
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending<LocalDate> { it })
            .map { (date, list) -> DayGroup(date, list.sortedByDescending { it.timestamp }, list.sumOf { it.netMineAgorot }) }
        val window = settings.trackingWindow
        AllTxUiState(
            filter = f,
            categories = cats.associateBy { it.id },
            categoryList = cats,
            availableMonths = months,
            unparsed = unparsed,
            groups = groups,
            count = unparsed.size + parsed.size,
            summary = TxSearch.summarize(parsed.map { it.toSummary() }),
            includesBeforeStart = parsed.any { it.txnKind != TxnKind.TRANSFER && !window.includes(it.timestamp, zone) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AllTxUiState(filter = filter.value))

    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun setCategory(c: String?) { filter.value = filter.value.copy(category = c) }
    fun setMonth(m: YearMonth?) { filter.value = filter.value.copy(month = m) }
    fun setUnparsedOnly(v: Boolean) { filter.value = filter.value.copy(unparsedOnly = v) }

    /** PRD: monthly CSV export, shared through the system share sheet. */
    suspend fun exportCsv(context: Context, month: YearMonth, categoryNames: Map<String, String>): Intent {
        val period = MonthPeriod(month, zone)
        val rows = graph.transactions.getAll()
            .filter { !it.isUnparsed && period.contains(it.timestamp) }
            .map { t ->
                val kindLabel = context.getString(
                    when (t.txnKind) {
                        TxnKind.EXPENSE -> R.string.kind_expense
                        TxnKind.REFUND -> R.string.kind_refund
                        TxnKind.TRANSFER -> R.string.kind_transfer
                    },
                )
                CsvBuilder.Row(t.toSummary(), categoryNames[t.categoryId] ?: t.categoryId, t.note, kindLabel)
            }
        val headers = listOf(
            R.string.csv_date, R.string.csv_merchant, R.string.csv_category, R.string.csv_amount_full,
            R.string.csv_amount_mine, R.string.csv_shared, R.string.csv_source, R.string.csv_kind, R.string.csv_note,
        ).map { context.getString(it) }
        val csv = CsvBuilder.build(rows, headers, zone)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "drift-$month.csv")
        file.writeText(csv, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "drift-$month.csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            context.getString(R.string.tx_export_title),
        )
    }
}
