package com.tamir.drift.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tamir.drift.R
import com.tamir.drift.data.repo.toJobSummaries
import com.tamir.drift.data.repo.toShiftSummaries
import com.tamir.drift.di.UiMessage
import com.tamir.drift.domain.work.QuickShift
import com.tamir.drift.domain.work.QuickShiftPlan
import com.tamir.drift.domain.work.ShiftCalculator
import com.tamir.drift.notify.DeepLink
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.announceShiftSaved
import com.tamir.drift.ui.components.BottomTab
import com.tamir.drift.ui.components.DriftBackdrop
import com.tamir.drift.ui.components.DriftBottomBar
import com.tamir.drift.ui.components.DriftIcons
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.LocalFeedback
import com.tamir.drift.ui.components.LocalTxQuickSheet
import com.tamir.drift.ui.components.Motion
import com.tamir.drift.ui.components.ScrimOverlay
import com.tamir.drift.ui.components.TxQuickSheet
import com.tamir.drift.ui.components.rememberFeedbackActions
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.screens.add.ManualAddScreen
import com.tamir.drift.ui.screens.budget.BudgetScreen
import com.tamir.drift.ui.screens.detail.TransactionDetailScreen
import com.tamir.drift.ui.screens.goal.GoalScreen
import com.tamir.drift.ui.screens.home.HomeScreen
import com.tamir.drift.ui.screens.inbox.UnsortedMerchantsScreen
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** "No date given" for the shift route's date argument. */
private const val NO_DATE = Long.MIN_VALUE

/** Where a pushed screen moves to as it comes in, and where it leaves to on the way back. */
private val PUSH_TOWARDS = AnimatedContentTransitionScope.SlideDirection.Start
private val PUSH_BACK_TOWARDS = AnimatedContentTransitionScope.SlideDirection.End

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
    val graph = LocalAppGraph.current
    val feedback = rememberFeedbackActions()
    // The expense whose long-press sheet is open (drawn here, above the bottom bar too). The id
    // stays set while the sheet fades out; "open" is what shows and hides it.
    val quickSheetTx = remember { mutableStateOf<Long?>(null) }
    val quickSheetOpen = remember { mutableStateOf(false) }
    val openQuickSheet: (Long) -> Unit = remember {
        { id: Long ->
            quickSheetTx.value = id
            quickSheetOpen.value = true
        }
    }
    // The "כמו בפעם הקודמת" row of the plus menu is built from the stored jobs and shifts.
    val jobs by graph.work.jobs.collectAsStateWithLifecycle(initialValue = emptyList())
    val shifts by graph.work.shifts.collectAsStateWithLifecycle(initialValue = emptyList())

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
                CompositionLocalProvider(LocalTxQuickSheet provides openQuickSheet) {
                    DriftNavHost(nav)
                }
            }
            if (activeTab != null) {
                DriftBottomBar(
                    active = activeTab,
                    onTab = { tab -> if (tab == activeTab) nav.resetTab(tab) else nav.navigateTopLevel(tab) },
                    onAdd = { showAddMenu = true },
                    modifier = Modifier.navigationBarsPadding(),
                    menuOpen = showAddMenu,
                )
            }
        }
        // Today's shift like the last one: hidden without an earlier shift to copy, and once
        // today already has a shift of that job. Worked out while the menu is open; while it
        // fades out the last plan stays, so the row does not vanish under the user's finger.
        val lastPlan = remember { arrayOfNulls<QuickShiftPlan>(1) }
        val quickShift: QuickShiftPlan? = remember(jobs, shifts, showAddMenu) {
            if (showAddMenu) lastPlan[0] = QuickShift.plan(LocalDate.now(), jobs.toJobSummaries(), shifts.toShiftSummaries())
            lastPlan[0]
        }
        // The menu fades in over a light scrim and rises a little; the plus turns into a close mark.
        ScrimOverlay(visible = showAddMenu && activeTab != null, onDismiss = { showAddMenu = false }) {
            AddMenu(
                onDismiss = { showAddMenu = false },
                onExpense = { showAddMenu = false; nav.navigate(Routes.ADD) },
                onShift = { showAddMenu = false; nav.navigate(Routes.shift()) },
                quickShift = quickShift,
                onQuickShift = { plan ->
                    showAddMenu = false
                    feedback.saved()
                    // Saved at once; the root message confirms the day total and offers the undo.
                    graph.scope.launch {
                        val id = graph.work.addShift(
                            jobId = plan.job.id,
                            date = LocalDate.ofEpochDay(plan.epochDay),
                            startTime = plan.startMinute,
                            endTime = plan.endMinute,
                            hours = plan.hours,
                            isShabbatRate = plan.isShabbatRate,
                            manualAmountAgorot = null,
                            note = null,
                            travelAgorot = plan.travelAgorot,
                        )
                        graph.announceShiftSaved(id, null)
                    }
                },
            )
        }
        TxQuickSheet(quickSheetTx.value, visible = quickSheetOpen.value, onDismiss = { quickSheetOpen.value = false })
        MessageHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 96.dp))
    }
}

/**
 * The app-wide inline message ("עודכנו 3 הוצאות של …", "נמחק · ביטול"): a quiet glass pill above
 * the bottom bar that shows the latest message from the app graph for a few seconds. A message
 * with an action shows it as a lime link at the end; tapping it runs the action (the undo) on the
 * app scope and closes the message.
 */
@Composable
private fun MessageHost(modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    var message by remember { mutableStateOf<UiMessage?>(null) }
    LaunchedEffect(graph) {
        graph.messages.collectLatest { next ->
            message = next
            delay(next.durationMillis)
            message = null
        }
    }
    val shown: UiMessage = message ?: return
    val actionLabel = shown.actionLabel
    val action = shown.action
    GlassCard(modifier.fillMaxWidth().background(DriftColors.Background, RoundedCornerShape(16.dp)), radius = 16.dp, borderColor = DriftColors.PlusBorder) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                shown.text, style = DriftType.Label13, color = DriftColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            )
            if (actionLabel != null && action != null) {
                Text(
                    actionLabel, style = DriftType.RowTitle, color = DriftColors.Lime, maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            message = null
                            graph.scope.launch { action() }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * PRD: the plus opens a small menu — "הוצאה" / "משמרת". A glass sheet anchored above the
 * bottom bar over a light scrim ([ScrimOverlay] draws the scrim and the short fade-and-rise);
 * tapping outside — or the back button — closes it.
 * With [quickShift] a first row "כמו בפעם הקודמת" saves today's shift in one tap: the job, its
 * hours (or start–end, or "יום") and the day total it will be saved with.
 */
@Composable
private fun AddMenu(
    onDismiss: () -> Unit,
    onExpense: () -> Unit,
    onShift: () -> Unit,
    quickShift: QuickShiftPlan?,
    onQuickShift: (QuickShiftPlan) -> Unit,
) {
    // The system back button closes the menu instead of leaving the screen under it. Registered
    // here — composed after the navigation host, whenever the menu opens — so it is the newest
    // back callback and wins over the host's own.
    BackHandler(onBack = onDismiss)
    GlassCard(
        Modifier
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, bottom = 96.dp)
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        radius = 22.dp,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            if (quickShift != null) {
                val start = quickShift.startMinute
                val end = quickShift.endMinute
                val what = when {
                    quickShift.job.isDaily -> stringResource(R.string.work_shift_day)
                    start != null && end != null -> "${ShiftCalculator.formatClock(start)}–${ShiftCalculator.formatClock(end)}"
                    else -> stringResource(R.string.work_hours_value, ShiftCalculator.formatHours(quickShift.hours))
                }
                AddMenuRow(
                    DriftIcons.Check, stringResource(R.string.add_menu_same_as_last), DriftColors.Lime,
                    onClick = { onQuickShift(quickShift) },
                    subtitle = "${quickShift.job.name} · $what",
                    trailing = stringResource(R.string.amount_with_shekel, Fmt.moneyExact(quickShift.totalAgorot)),
                )
            }
            AddMenuRow(DriftIcons.Plus, stringResource(R.string.add_menu_expense), DriftColors.Orange, onExpense)
            AddMenuRow(DriftIcons.Work, stringResource(R.string.add_menu_shift), DriftColors.Lime, onShift)
        }
    }
}

@Composable
private fun AddMenuRow(icon: ImageVector, text: String, color: Color, onClick: () -> Unit, subtitle: String? = null, trailing: String? = null) {
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(text, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Text(trailing, style = DriftType.RowAmount, color = DriftColors.Text, maxLines = 1)
        }
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

/** True for the four tab roots (they only fade); every other destination is a pushed screen. */
private fun NavBackStackEntry.isTab(): Boolean {
    val base: String = destination.route?.substringBefore('?') ?: return false
    return topLevel.containsKey(base)
}

/**
 * Transitions: a tab only fades ([Motion.TAB_FADE_MS]). A pushed screen (detail, add, settings,
 * lists) slides in over the full width and fades in ([Motion.PUSH_MS]) while the screen under it
 * fades out; going back it slides out the way it came. The slide follows the layout direction:
 * [PUSH_TOWARDS] = Start is the platform's forward motion — in this right-to-left app the new
 * screen comes in from the left and moves right, the way the header's back arrow points.
 * With animations off (the settings switch, or the system animator scale at zero) screens snap.
 */
@Composable
private fun DriftNavHost(nav: NavHostController) {
    val animations = LocalFeedback.current.animations
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = {
            when {
                !animations -> EnterTransition.None
                targetState.isTab() -> fadeIn(tween(Motion.TAB_FADE_MS))
                else -> slideIntoContainer(PUSH_TOWARDS, tween(Motion.PUSH_MS)) + fadeIn(tween(Motion.PUSH_MS))
            }
        },
        exitTransition = {
            when {
                !animations -> ExitTransition.None
                targetState.isTab() -> fadeOut(tween(Motion.TAB_FADE_MS))
                else -> fadeOut(tween(Motion.PUSH_MS))
            }
        },
        popEnterTransition = {
            when {
                !animations -> EnterTransition.None
                initialState.isTab() -> fadeIn(tween(Motion.TAB_FADE_MS))
                else -> fadeIn(tween(Motion.PUSH_MS))
            }
        },
        popExitTransition = {
            when {
                !animations -> ExitTransition.None
                initialState.isTab() -> fadeOut(tween(Motion.TAB_FADE_MS))
                else -> slideOutOfContainer(PUSH_BACK_TOWARDS, tween(Motion.PUSH_MS)) + fadeOut(tween(Motion.PUSH_MS))
            }
        },
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
                onOpenUnsorted = { nav.navigate(Routes.UNSORTED) },
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
                onAddShiftOn = { day -> nav.navigate(Routes.shift(epochDay = day.toEpochDay())) },
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
            "${Routes.SHIFT}?${Routes.ARG_ID}={${Routes.ARG_ID}}&${Routes.ARG_DATE}={${Routes.ARG_DATE}}",
            arguments = listOf(
                navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L },
                // An epoch day; NO_DATE = open on today.
                navArgument(Routes.ARG_DATE) { type = NavType.LongType; defaultValue = NO_DATE },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
            val day = entry.arguments?.getLong(Routes.ARG_DATE) ?: NO_DATE
            AddShiftScreen(
                shiftId = id.takeIf { it >= 0L },
                onDone = { nav.popBackStack() },
                initialEpochDay = day.takeIf { it != NO_DATE },
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
        ) { entry ->
            TransactionDetailScreen(
                id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L,
                onBack = { nav.popBackStack() },
                onOpenCalibration = { txId -> nav.navigate(Routes.calibration(txId)) },
            )
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
        composable(
            "${Routes.CALIBRATION}?${Routes.ARG_TX}={${Routes.ARG_TX}}",
            arguments = listOf(navArgument(Routes.ARG_TX) { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val txId = entry.arguments?.getLong(Routes.ARG_TX) ?: -1L
            CalibrationScreen(onBack = { nav.popBackStack() }, prefillTransactionId = txId.takeIf { it >= 0L })
        }
        composable(Routes.UNSORTED) { UnsortedMerchantsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.DEBUG) {
            DebugLogScreen(onBack = { nav.popBackStack() }, onOpenTransaction = { nav.navigate(Routes.detail(it)) })
        }
    }
}
