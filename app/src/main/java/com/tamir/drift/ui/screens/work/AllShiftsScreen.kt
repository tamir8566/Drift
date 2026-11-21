package com.tamir.drift.ui.screens.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.db.ShiftEntity
import com.tamir.drift.data.db.totalAgorot
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import java.time.LocalDate
import java.time.YearMonth

/**
 * "כל המשמרות": every shift, newest first, grouped by month with the month's income and number of
 * shifts. A tap opens the shift for editing — the way to reach shifts older than the few the work
 * screen shows.
 */
@Composable
fun AllShiftsScreen(onBack: () -> Unit, onEditShift: (Long) -> Unit) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val shifts by graph.work.shifts.collectAsStateWithLifecycle(initialValue = null)
    val jobs by graph.work.jobs.collectAsStateWithLifecycle(initialValue = emptyList())
    val jobsById = remember(jobs) { jobs.associateBy { it.id } }
    val loaded = shifts
    // The DAO order is newest first, and groupBy keeps it — so are the months and the shifts inside them.
    val groups: List<Pair<YearMonth, List<ShiftEntity>>> = remember(loaded) {
        loaded.orEmpty().groupBy { YearMonth.from(LocalDate.ofEpochDay(it.date)) }.toList()
    }
    val thisYear = LocalDate.now().year

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.work_all_shifts), onBack = onBack, backDescription = stringResource(R.string.nav_back)) }
        if (loaded != null && loaded.isEmpty()) {
            item { EmptyNote(stringResource(R.string.work_no_shifts)) }
        }
        groups.forEach { (month, list) ->
            item(key = "m$month") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp, start = 2.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Fmt.monthLabel(context, month, withYear = month.year != thisYear),
                        style = DriftType.SectionTitle, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.work_month_group_total, list.size, Fmt.moneyAgorot(list.sumOf { it.totalAgorot })),
                        style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1,
                    )
                }
            }
            items(list, key = { it.id }) { shift ->
                ShiftRow(shift, jobsById[shift.jobId], onClick = { onEditShift(shift.id) })
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
