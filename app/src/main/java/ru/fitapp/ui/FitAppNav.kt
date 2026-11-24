package ru.fitapp.ui

import androidx.compose.runtime.Composable
import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.LocalDate
import ru.fitapp.core.LogTags
import ru.fitapp.ui.calendar.CalendarScreen
import ru.fitapp.ui.capture.CaptureScreen
import ru.fitapp.ui.day.DayScreen
import ru.fitapp.ui.meal.MealScreen
import ru.fitapp.ui.onboarding.KeyScreen
import ru.fitapp.ui.onboarding.KeyScreenMode
import ru.fitapp.ui.onboarding.ProfileFormScreen
import ru.fitapp.ui.report.ReportScreen
import ru.fitapp.ui.settings.SettingsScreen
import ru.fitapp.ui.settings.StatsScreen

/** Маршруты навигации (§9 PLAN). */
object Routes {
    const val ONBOARDING_KEY = "onboarding/key"
    const val ONBOARDING_PROFILE = "onboarding/profile"
    const val TODAY = "today"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"
    const val SETTINGS_KEY = "settings/key"
    const val SETTINGS_PROFILE = "settings/profile"
    const val STATS = "settings/stats"
    const val CAPTURE = "capture"
    const val MEAL = "meal/{mealId}"
    const val DAY = "day/{date}"
    const val REPORT = "report/{date}"

    fun meal(mealId: String) = "meal/$mealId"
    fun day(date: LocalDate) = "day/$date"
    fun report(date: LocalDate) = "report/$date"
}

/** Главный NavHost: онбординг до завершения анкеты, затем скелет «Сегодня / Календарь / Настройки». */
@Composable
fun FitAppNavHost(startOnboarding: Boolean) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (startOnboarding) Routes.ONBOARDING_KEY else Routes.TODAY,
    ) {
        // --- Онбординг ---
        composable(Routes.ONBOARDING_KEY) {
            KeyScreen(
                mode = KeyScreenMode.ONBOARDING,
                onBack = null,
                onValid = {
                    navController.navigate(Routes.ONBOARDING_PROFILE) {
                        popUpTo(Routes.ONBOARDING_KEY) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ONBOARDING_PROFILE) {
            ProfileFormScreen(
                editMode = false,
                onBack = null,
                onSaved = {
                    navController.navigate(Routes.TODAY) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                },
            )
        }

        // --- Основные вкладки ---
        composable(Routes.TODAY) {
            DayScreen(
                date = LocalDate.now(),
                isToday = true,
                navController = navController,
                onOpenMeal = { navController.navigate(Routes.meal(it)) },
                onOpenReport = { navController.navigate(Routes.report(it)) },
                onCapture = { navController.navigate(Routes.CAPTURE) },
            )
        }
        composable(Routes.CALENDAR) {
            CalendarScreen(
                navController = navController,
                onOpenDay = { navController.navigate(Routes.day(it)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(navController = navController)
        }

        // --- Настройки: ключ, цели, статистика ---
        composable(Routes.SETTINGS_KEY) {
            KeyScreen(
                mode = KeyScreenMode.SETTINGS,
                onBack = { navController.popBackStack() },
                onValid = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS_PROFILE) {
            ProfileFormScreen(
                editMode = true,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(Routes.STATS) {
            StatsScreen(onBack = { navController.popBackStack() })
        }

        // --- Съёмка ---
        composable(Routes.CAPTURE) {
            CaptureScreen(onBack = { navController.popBackStack() })
        }

        // --- Карточка ---
        composable(
            route = Routes.MEAL,
            arguments = listOf(navArgument("mealId") { type = NavType.StringType }),
        ) { entry ->
            val mealId = entry.arguments?.getString("mealId").orEmpty()
            MealScreen(
                mealId = mealId,
                onBack = { navController.popBackStack() },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                },
            )
        }

        // --- Прошлый день из календаря (та же лента, только чтение) ---
        composable(
            route = Routes.DAY,
            arguments = listOf(navArgument("date") { type = NavType.StringType }),
        ) { entry ->
            val date = parseRouteDate(entry.arguments?.getString("date"))
            DayScreen(
                date = date,
                isToday = false,
                navController = null,
                onBack = { navController.popBackStack() },
                onOpenMeal = { navController.navigate(Routes.meal(it)) },
                onOpenReport = { navController.navigate(Routes.report(it)) },
                onCapture = {},
            )
        }

        // --- Отчёт дня ---
        composable(
            route = Routes.REPORT,
            arguments = listOf(navArgument("date") { type = NavType.StringType }),
        ) { entry ->
            val date = parseRouteDate(entry.arguments?.getString("date"))
            ReportScreen(date = date, onBack = { navController.popBackStack() })
        }
    }
}

/** Переключение вкладок нижнего меню с сохранением состояния стека. */
fun navigateToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Дата из аргумента маршрута; при мусоре открывается сегодняшний день — это стоит видеть в логе. */
private fun parseRouteDate(raw: String?): LocalDate =
    runCatching { LocalDate.parse(raw.orEmpty()) }.getOrElse {
        Log.w(LogTags.UI, "nav: дата маршрута не разобрана «$raw», открыт сегодняшний день")
        LocalDate.now()
    }
