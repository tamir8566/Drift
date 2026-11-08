package com.tamir.drift.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.sourceName
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType

/** PRD: the last 20 raw captured texts with their parse outcome. */
@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val captures by graph.db.rawCaptures().observeLatest(20).collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.debug_title), onBack = onBack, backDescription = stringResource(R.string.nav_back)) }
        if (captures.isEmpty()) item { EmptyNote(stringResource(R.string.debug_empty)) }
        items(captures, key = { it.id }) { c ->
            GlassCard(Modifier.fillMaxWidth(), radius = 16.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${sourceName(c.source)} · ${c.senderOrPackage}", style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${Fmt.shortDate(c.capturedAt)} ${Fmt.time(c.capturedAt)}", style = DriftType.Label11, color = DriftColors.TextDim)
                    }
                    Text(c.text, style = DriftType.Label12, color = DriftColors.Text, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    val resultColor = when {
                        c.result.startsWith("PARSED") -> DriftColors.Teal
                        c.result.startsWith("DUPLICATE") -> DriftColors.TextSecondary
                        else -> DriftColors.Orange
                    }
                    Text(stringResource(R.string.debug_result, c.result.ifBlank { "—" }), style = DriftType.Label11, color = resultColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    c.transactionId?.takeIf { it > 0 }?.let {
                        Text(stringResource(R.string.debug_linked, it), style = DriftType.Label11, color = DriftColors.TextDim)
                    }
                }
            }
        }
    }
}
