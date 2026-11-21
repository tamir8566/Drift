package com.tamir.drift.ui.screens.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.db.Origin
import com.tamir.drift.data.repo.toSummaries
import com.tamir.drift.domain.classify.UnsortedMerchant
import com.tamir.drift.domain.classify.UnsortedMerchants
import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.MerchantRuleModel
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.ChoiceRow
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.launch

/**
 * "בתי עסק מחכים לסיווג": every merchant whose expenses sit in "אחר" without a learned rule, the
 * biggest first, each with the category chips. One tap classifies all its past charges and — by
 * the learned rule — its future ones. "באמת אחר" records the rule too, so the merchant leaves the
 * list. Opened from the home attention row.
 */
@Composable
fun UnsortedMerchantsScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val loadedTxs by graph.transactions.all.collectAsStateWithLifecycle(initialValue = null)
    val rules by graph.categories.rules.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by graph.categories.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val txs = loadedTxs
    val merchants: List<UnsortedMerchant> = remember(txs, rules) {
        UnsortedMerchants.find(
            txs.orEmpty().toSummaries(),
            rules.map { MerchantRuleModel(it.keyword, it.categoryId, it.origin == Origin.LEARNED) },
        )
    }
    val reallyOther = stringResource(R.string.unsorted_really_other)
    val options: List<Pair<String, String>> = remember(categories, reallyOther) {
        categories.filter { it.id != CategoryKeys.OTHER }.map { it.id to it.name } + (CategoryKeys.OTHER to reallyOther)
    }
    val context = graph.appContext

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { ScreenHeader(stringResource(R.string.unsorted_title), onBack = onBack, backDescription = stringResource(R.string.nav_back)) }
        item(key = "hint") {
            Text(stringResource(R.string.unsorted_hint), style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (txs != null && merchants.isEmpty()) {
            item(key = "empty") { EmptyNote(stringResource(R.string.unsorted_empty)) }
        }
        items(merchants, key = { "m:" + it.key }) { m ->
            GlassCard(Modifier.fillMaxWidth(), radius = 20.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(m.displayName, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (m.count == 1) stringResource(R.string.unsorted_count_one) else stringResource(R.string.unsorted_count_many, m.count),
                                style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1,
                            )
                        }
                        Text(stringResource(R.string.amount_with_shekel, Fmt.signedMoney(m.total)), style = DriftType.RowAmount, color = DriftColors.Text, maxLines = 1)
                    }
                    ChoiceRow(
                        options = options,
                        selected = null,
                        onSelect = { picked ->
                            // On the app scope: the rule and the rows are written even if the screen is left at once.
                            graph.scope.launch {
                                val moved = graph.transactions.recategorizeMerchant(m.displayName, picked)
                                graph.alertChecker.checkAndNotify()
                                graph.showMessage(
                                    when {
                                        picked == CategoryKeys.OTHER -> context.getString(R.string.unsorted_kept_other, m.displayName)
                                        moved == 1 -> context.getString(R.string.unsorted_moved_one, m.displayName)
                                        else -> context.getString(R.string.recat_message_many, moved, m.displayName)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
        item(key = "end") { Spacer(Modifier.height(4.dp)) }
    }
}
