package com.tamir.drift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamir.drift.R
import com.tamir.drift.ui.theme.DriftColors

enum class BottomTab { HOME, WHERE, BUDGET, SAVINGS }

/**
 * Bottom bar from the mockup: line icons only, no background; active in lime, others dim;
 * the plus in the middle is a 52dp glass circle with a lime 45% border.
 * Order (RTL, start→end): home · where · + · budget · savings.
 */
@Composable
fun DriftBottomBar(active: BottomTab?, onTab: (BottomTab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(DriftIcons.Home, stringResource(R.string.nav_home), active == BottomTab.HOME) { onTab(BottomTab.HOME) }
        BarIcon(DriftIcons.Bars, stringResource(R.string.nav_where), active == BottomTab.WHERE) { onTab(BottomTab.WHERE) }
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(DriftColors.GlassFill, CircleShape)
                .border(1.dp, DriftColors.PlusBorder, CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = DriftColors.Lime), onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DriftIcons.Plus, stringResource(R.string.nav_add), tint = DriftColors.Lime, modifier = Modifier.size(22.dp))
        }
        BarIcon(DriftIcons.Budget, stringResource(R.string.nav_budget), active == BottomTab.BUDGET) { onTab(BottomTab.BUDGET) }
        BarIcon(DriftIcons.Savings, stringResource(R.string.nav_savings), active == BottomTab.SAVINGS) { onTab(BottomTab.SAVINGS) }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = DriftColors.Lime, bounded = false), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (active) DriftColors.Lime else DriftColors.TextDim, modifier = Modifier.size(22.dp))
    }
}
