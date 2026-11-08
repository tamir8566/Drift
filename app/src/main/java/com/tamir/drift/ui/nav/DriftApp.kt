package com.tamir.drift.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tamir.drift.ui.components.BottomTab
import com.tamir.drift.ui.components.DriftBackdrop
import com.tamir.drift.ui.components.DriftBottomBar
import com.tamir.drift.ui.screens.add.ManualAddScreen
import com.tamir.drift.ui.screens.budget.BudgetScreen
import com.tamir.drift.ui.screens.detail.TransactionDetailScreen
import com.tamir.drift.ui.screens.home.HomeScreen
import com.tamir.drift.ui.screens.savings.SavingsScreen
import com.tamir.drift.ui.screens.settings.CalibrationScreen
import com.tamir.drift.ui.screens.settings.DebugLogScreen
import com.tamir.drift.ui.screens.settings.SettingsScreen
import com.tamir.drift.ui.screens.transactions.AllTransactionsScreen
import com.tamir.drift.ui.screens.where.WhereScreen

private val topLevel = mapOf(
    Routes.HOME to BottomTab.HOME,
    Routes.WHERE to BottomTab.WHERE,
    Routes.BUDGET to BottomTab.BUDGET,
    Routes.SAVINGS to BottomTab.SAVINGS,
)

@Composable
fun DriftApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val baseRoute = route?.substringBefore('?')
    val activeTab = topLevel[baseRoute]

    DriftBackdrop {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                DriftNavHost(nav)
            }
            if (activeTab != null) {
                DriftBottomBar(
                    active = activeTab,
                    onTab = { tab -> nav.navigateTopLevel(tab) },
                    onAdd = { nav.navigate(Routes.ADD) },
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(tab: BottomTab) {
    val target = when (tab) {
        BottomTab.HOME -> Routes.HOME
        BottomTab.WHERE -> Routes.WHERE
        BottomTab.BUDGET -> Routes.BUDGET
        BottomTab.SAVINGS -> Routes.SAVINGS
    }
    navigate(target) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
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
        composable(Routes.BUDGET) { BudgetScreen() }
        composable(Routes.SAVINGS) { SavingsScreen() }
        composable(Routes.ADD) { ManualAddScreen(onDone = { nav.popBackStack() }) }
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
            )
        }
        composable(Routes.CALIBRATION) { CalibrationScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.DEBUG) { DebugLogScreen(onBack = { nav.popBackStack() }) }
    }
}
