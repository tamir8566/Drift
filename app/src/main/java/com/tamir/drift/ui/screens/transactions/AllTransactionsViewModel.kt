package com.tamir.drift.ui.screens.transactions

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamir.drift.R
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.repo.toSummary
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.export.CsvBuilder
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

data class DayGroup(val date: LocalDate, val items: List<TransactionEntity>, val total: Long)

data class TxFilter(val category: String? = null, val month: YearMonth? = null, val query: String = "")

data class AllTxUiState(
    val filter: TxFilter = TxFilter(),
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val categoryList: List<CategoryEntity> = emptyList(),
    val availableMonths: List<YearMonth> = emptyList(),
    val unparsed: List<TransactionEntity> = emptyList(),
    val groups: List<DayGroup> = emptyList(),
    val count: Int = 0,
)

class AllTransactionsViewModel(private val graph: AppGraph, initialCategory: String?, initialMonth: String?) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val filter = MutableStateFlow(
        TxFilter(category = initialCategory, month = initialMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }),
    )

    val state: StateFlow<AllTxUiState> = combine(graph.transactions.all, graph.categories.categories, filter) { txs, cats, f ->
        val months = txs.map { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }.distinct().sortedDescending()
        val q = f.query.trim().lowercase()
        val filtered = txs.filter { t ->
            (f.category == null || t.categoryId == f.category) &&
                (f.month == null || MonthPeriod(f.month, zone).contains(t.timestamp)) &&
                (q.isEmpty() || t.merchant.lowercase().contains(q) || (t.note?.lowercase()?.contains(q) ?: false) || (t.rawText?.lowercase()?.contains(q) ?: false))
        }
        val unparsed = filtered.filter { it.isUnparsed }.sortedByDescending { it.timestamp }
        val groups = filtered.filter { !it.isUnparsed }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending<LocalDate> { it })
            .map { (date, list) -> DayGroup(date, list.sortedByDescending { it.timestamp }, list.sumOf { it.amountMine }) }
        AllTxUiState(
            filter = f,
            categories = cats.associateBy { it.id },
            categoryList = cats,
            availableMonths = months,
            unparsed = unparsed,
            groups = groups,
            count = filtered.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AllTxUiState(filter = filter.value))

    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun setCategory(c: String?) { filter.value = filter.value.copy(category = c) }
    fun setMonth(m: YearMonth?) { filter.value = filter.value.copy(month = m) }

    /** PRD: monthly CSV export, shared through the system share sheet. */
    suspend fun exportCsv(context: Context, month: YearMonth, categoryNames: Map<String, String>): Intent {
        val period = MonthPeriod(month, zone)
        val rows = graph.transactions.getAll()
            .filter { !it.isUnparsed && period.contains(it.timestamp) }
            .map { t -> CsvBuilder.Row(t.toSummary(), categoryNames[t.categoryId] ?: t.categoryId, t.note) }
        val headers = listOf(
            R.string.csv_date, R.string.csv_merchant, R.string.csv_category, R.string.csv_amount_full,
            R.string.csv_amount_mine, R.string.csv_shared, R.string.csv_source, R.string.csv_note,
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
