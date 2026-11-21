package com.tamir.drift.ui.nav

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
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
import com.tamir.drift.notify.DeepLink
import com.tamir.drift.ui.LocalAppGraph
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
import com.tamir.drift.ui.screens.work.AllShiftsScreen
import com.tamir.drift.ui.screens.work.WorkScreen
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.YearMonth

private val topLevel = mapOf(
    Routes.HOME to BottomTab.HOME,
    Routes.WHERE to BottomTab.WHERE,
    Routes.WORK to BottomTab.WORK,
    Routes.GOAL to BottomTab.GOAL,
)

/**
 * @param deepLink the screen a tapped notification asked for ([DeepLink]), or null.
 * @param onDeepLinkConsumed called once the navigation happened, so it does not repeat.
 */
@Composable
fun DriftApp(deepLink: String? = null, onDeepLinkConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val baseRoute = route?.substringBefore('?')
    val activeTab = topLevel[baseRoute]
    var showAddMenu by remember { mutableStateOf(false) }

    // Notifications: budget alerts → budget, weekly work summary → goal, weekly spend summary → where.
    LaunchedEffect(deepLink) {
        val target = deepLink ?: return@LaunchedEffect
        showAddMenu = false
        when (target) {
            DeepLink.BUDGET -> nav.navigate(Routes.BUDGET) { launchSingleTop = true }
            DeepLink.GOAL -> nav.navigateTopLevel(BottomTab.GOAL)
            DeepLink.WHERE -> nav.navigateTopLevel(BottomTab.WHERE)
            DeepLink.WORK -> nav.navigateTopLevel(BottomTab.WORK)
            DeepLink.SETTINGS -> nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
        }
        onDeepLinkConsumed()
    }

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
        MessageHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 96.dp))
    }
}

/**
 * The app-wide inline message ("עודכנו 3 הוצאות של …"): a quiet glass pill above the bottom bar
 * that shows the latest message from the app graph for a few seconds.
 */
@Composable
private fun MessageHost(modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(graph) {
        graph.messages.collectLatest { text ->
            message = text
            delay(MESSAGE_MILLIS)
            message = null
        }
    }
    val text = message ?: return
    GlassCard(modifier.fillMaxWidth().background(DriftColors.Background, RoundedCornerShape(16.dp)), radius = 16.dp, borderColor = DriftColors.PlusBorder) {
        Text(
            text, style = DriftType.Label13, color = DriftColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

private const val MESSAGE_MILLIS = 3_500L

/**
 * PRD: the plus opens a small menu — "הוצאה" / "משמרת". A glass sheet anchored above the
 * bottom bar over a light scrim; tapping outside — or the back button — closes it.
 */
@Composable
private fun AddMenu(onDismiss: () -> Unit, onExpense: () -> Unit, onShift: () -> Unit) {
    // The system back button closes the menu instead of leaving the screen under it. Registered
    // here — composed after the navigation host, whenever the menu opens — so it is the newest
    // back callback and wins over the host's own.
    BackHandler(onBack = onDismiss)
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
                // One tap on a category card → that category's expenses of this month.
                onOpenCategory = { nav.navigate(Routes.transactions(category = it, month = YearMonth.now().toString())) },
                onOpenTransaction = { nav.navigate(Routes.detail(it)) },
                onOpenAll = { nav.navigate(Routes.transactions()) },
                // "לפי קטגוריה → הכול" is the breakdown itself: the "לאן הכסף הולך" tab.
                onOpenWhere = { nav.navigateTopLevel(BottomTab.WHERE) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenSearch = { nav.navigate(Routes.transactions(focusSearch = true)) },
                onOpenUnparsed = { nav.navigate(Routes.transactions(unparsedOnly = true)) },
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
                onOpenAllShifts = { nav.navigate(Routes.SHIFTS) },
            )
        }
        composable(Routes.SHIFTS) {
            AllShiftsScreen(onBack = { nav.popBackStack() }, onEditShift = { nav.navigate(Routes.shift(it)) })
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
            "${Routes.TRANSACTIONS}?${Routes.ARG_CATEGORY}={${Routes.ARG_CATEGORY}}&${Routes.ARG_MONTH}={${Routes.ARG_MONTH}}" +
                "&${Routes.ARG_SEARCH}={${Routes.ARG_SEARCH}}&${Routes.ARG_UNPARSED}={${Routes.ARG_UNPARSED}}",
            arguments = listOf(
                navArgument(Routes.ARG_CATEGORY) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Routes.ARG_MONTH) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Routes.ARG_SEARCH) { type = NavType.BoolType; defaultValue = false },
                navArgument(Routes.ARG_UNPARSED) { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            AllTransactionsScreen(
                initialCategory = entry.arguments?.getString(Routes.ARG_CATEGORY),
                initialMonth = entry.arguments?.getString(Routes.ARG_MONTH),
                focusSearch = entry.arguments?.getBoolean(Routes.ARG_SEARCH) ?: false,
                unparsedOnly = entry.arguments?.getBoolean(Routes.ARG_UNPARSED) ?: false,
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
        composable(Routes.DEBUG) {
            DebugLogScreen(onBack = { nav.popBackStack() }, onOpenTransaction = { nav.navigate(Routes.detail(it)) })
        }
    }
}
