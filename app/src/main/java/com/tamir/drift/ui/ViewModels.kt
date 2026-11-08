package com.tamir.drift.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tamir.drift.di.AppGraph

/** The app graph, provided at the root so screens can build their view models. */
val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }

/** Manual-DI view model creation: `val vm = driftViewModel { HomeViewModel(graph) }`. */
@Composable
inline fun <reified VM : ViewModel> driftViewModel(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key, factory = viewModelFactory { initializer { create() } })
