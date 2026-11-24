package ru.fitapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.fitapp.FitAppApplication

/**
 * Минимальная фабрика ViewModel-ов без Hilt (§9, п.10):
 * зависимости берутся из ручной DI-композиции FitAppApplication.
 */
inline fun <reified VM : ViewModel> factoryOf(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

/** Приложение с ручной DI-композицией из любого composable. */
@Composable
fun fitApp(): FitAppApplication = LocalContext.current.applicationContext as FitAppApplication
