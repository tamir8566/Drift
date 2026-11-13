package com.tamir.drift.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tamir.drift.R
import com.tamir.drift.ui.components.BottomTab
import com.tamir.drift.ui.components.DriftBackdrop
import com.tamir.drift.ui.components.DriftBottomBar
import com.tamir.drift.ui.components.DriftIcons
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.screens.add.ManualAddScreen
import com.tamir.drift.ui.screens.budget.BudgetScreen
import com.tamir.drift.ui.screens.detail.TransactionDetailScreen
import com.tamir.drift.ui.screens.goal.GoalScreen
import com.tamir.drift.ui.screens.home.HomeScreen
import com.tamir.drift.ui.screens.settings.CalibrationScreen
import com.tamir.drift.ui.screens.settings.DebugLogScreen
import com.tamir.drift.ui.screens.settings.SettingsScreen
import com.tamir.drift.ui.screens.transactions.AllTransactionsScreen
import com.tamir.drift.ui.screens.where.WhereScreen
import com.tamir.drift.ui.screens.work.AddShiftScreen
import com.tamir.drift.ui.screens.work.WorkScreen
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType

private val topLevel = mapOf(
    Routes.HOME to BottomTab.HOME,
    Routes.WHERE to BottomTab.WHERE,
    Routes.WORK to BottomTab.WORK,
    Routes.GOAL to BottomTab.GOAL,
)

@Composable
fun DriftApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val baseRoute = route?.substringBefore('?')
    val activeTab = topLevel[baseRoute]
    var showAddMenu by remember { mutableStateOf(false) }

    DriftBackdrop {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                DriftNavHost(nav)
            }
            if (activeTab != null) {
                DriftBottomBar(
                    active = activeTab,
                    onTab = { tab -> if (tab == activeTab) nav.resetTab(tab) else nav.navigateTopLevel(tab) },
                    onAdd = { showAddMenu = true },
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        }
        if (showAddMenu && activeTab != null) {
            AddMenu(
                onDismiss = { showAddMenu = false },
                onExpense = { showAddMenu = false; nav.navigate(Routes.ADD) },
                onShift = { showAddMenu = false; nav.navigate(Routes.shift()) },
            )
        }
    }
}

/**
 * PRD: the plus opens a small menu — "הוצאה" / "משמרת". A glass sheet anchored above the
 * bottom bar over a light scrim; tapping outside closes it.
 */
@Composable
private fun AddMenu(onDismiss: () -> Unit, onExpense: () -> Unit, onShift: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        GlassCard(
            Modifier
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 96.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            radius = 22.dp,
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                AddMenuRow(DriftIcons.Plus, stringResource(R.string.add_menu_expense), DriftColors.Orange, onExpense)
                AddMenuRow(DriftIcons.Work, stringResource(R.string.add_menu_shift), DriftColors.Lime, onShift)
            }
        }
    }
}

@Composable
private fun AddMenuRow(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, text, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(text, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun NavHostController.navigateTopLevel(tab: BottomTab) {
    val target = when (tab) {
        BottomTab.HOME -> Routes.HOME
        BottomTab.WHERE -> Routes.WHERE
        BottomTab.WORK -> Routes.WORK
        BottomTab.GOAL -> Routes.GOAL
    }
    navigate(target) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun BottomTab.route(): String = when (this) {
    BottomTab.HOME -> Routes.HOME
    BottomTab.WHERE -> Routes.WHERE
    BottomTab.WORK -> Routes.WORK
    BottomTab.GOAL -> Routes.GOAL
}

/**
 * Tapping the already-active tab brings its root screen back fresh: filters cleared, scrolled to
 * the top, and anything the tab had pushed on top of it removed.
 */
private fun NavHostController.resetTab(tab: BottomTab) {
    // The bar is only shown on a tab's root destination, so the current destination is that root.
    val rootId = currentDestination?.id ?: return
    navigate(tab.route()) {
        popUpTo(rootId) { inclusive = true; saveState = false }
        launchSingleTop = false
        restoreState = false
    }
}

@Composable
private fun DriftNavHost(nav: NavHostController) {
    val fade = 180
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(fade)) },
        exitTransition = { fadeOut(tween(fade)) },
        popEnterTransition = { fadeIn(tween(fade)) },
        popExitTransition = { fadeOut(tween(fade)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenCategory = { nav.navigate(Routes.where(it)) },
                onOpenTransaction = { nav.navigate(Routes.detail(it)) },
                onOpenAll = { nav.navigate(Routes.transactions()) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenSearch = { nav.navigate(Routes.transactions()) },
                onOpenBudget = { nav.navigate(Routes.BUDGET) },
                onOpenGoal = { nav.navigateTopLevel(BottomTab.GOAL) },
            )
        }
        composable(
            "${Routes.WHERE}?${Routes.ARG_CATEGORY}={${Routes.ARG_CATEGORY}}",
            arguments = listOf(navArgument(Routes.ARG_CATEGORY) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            WhereScreen(
                initialCategory = entry.arguments?.getString(Routes.ARG_CATEGORY),
                onOpenCategoryList = { cat, month -> nav.navigate(Routes.transactions(cat, month)) },
                onOpenTransaction = { nav.navigate(Routes.detail(it)) },
            )
        }
        composable(Routes.BUDGET) { BudgetScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.WORK) {
            WorkScreen(
                onAddShift = { nav.navigate(Routes.shift()) },
                onEditShift = { nav.navigate(Routes.shift(it)) },
            )
        }
        composable(Routes.GOAL) { GoalScreen() }
        composable(Routes.ADD) { ManualAddScreen(onDone = { nav.popBackStack() }) }
        composable(
            "${Routes.SHIFT}?${Routes.ARG_ID}={${Routes.ARG_ID}}",
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
            AddShiftScreen(shiftId = id.takeIf { it >= 0L }, onDone = { nav.popBackStack() })
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
        ) { entry ->
            TransactionDetailScreen(id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L, onBack = { nav.popBackStack() })
        }
        composable(
            "${Routes.TRANSACTIONS}?${Routes.ARG_CATEGORY}={${Routes.ARG_CATEGORY}}&${Routes.ARG_MONTH}={${Routes.ARG_MONTH}}",
            arguments = listOf(
                navArgument(Routes.ARG_CATEGORY) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Routes.ARG_MONTH) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            AllTransactionsScreen(
                initialCategory = entry.arguments?.getString(Routes.ARG_CATEGORY),
                initialMonth = entry.arguments?.getString(Routes.ARG_MONTH),
                onBack = { nav.popBackStack() },
                onOpenTransaction = { nav.navigate(Routes.detail(it)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenCalibration = { nav.navigate(Routes.CALIBRATION) },
                onOpenDebug = { nav.navigate(Routes.DEBUG) },
                onOpenAll = { nav.navigate(Routes.transactions()) },
                onOpenBudget = { nav.navigate(Routes.BUDGET) },
            )
        }
        composable(Routes.CALIBRATION) { CalibrationScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.DEBUG) { DebugLogScreen(onBack = { nav.popBackStack() }) }
    }
}
