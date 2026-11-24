package ru.fitapp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import ru.fitapp.R

private data class BottomTab(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/** Нижняя панель «Сегодня / Календарь / Настройки» (§9 PLAN). */
@Composable
fun FitAppBottomBar(navController: NavController) {
    val tabs = remember {
        listOf(
            BottomTab(Routes.TODAY, R.string.nav_today, Icons.Filled.Today),
            BottomTab(Routes.CALENDAR, R.string.nav_calendar, Icons.Filled.CalendarMonth),
            BottomTab(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
        )
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        tabs.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(navController, tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}
