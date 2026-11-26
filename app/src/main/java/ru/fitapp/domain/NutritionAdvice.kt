package ru.fitapp.domain

import kotlin.math.min
import kotlin.math.roundToInt

/** Тон совета: что уже хорошо, что стоит поправить и просто пояснение. */
enum class AdviceTone { GOOD, WARN, TIP }

/**
 * Один совет по питанию: короткий заголовок и конкретное действие с числами,
 * посчитанными из того же отчёта. Текст не содержит оценок «на глаз».
 */
data class Advice(
    val id: String,
    val tone: AdviceTone,
    val title: String,
    val detail: String,
)

/**
 * Советы по питанию из уже посчитанного дня (§8.3 PLAN).
 *
 * Правила:
 * - совет опирается только на вердикты и интервалы отчёта, поэтому не спорит с цифрами;
 * - каждое «не хватает» переводится в продукты и порции, а не в абстрактное «ешьте больше»;
 * - формулировки про еду и режим, без диагнозов и медицинских предписаний;
 * - не больше [MAX_ITEMS] пунктов, чтобы экран оставался читаемым.
 */
object NutritionAdvice {

    const val MAX_ITEMS = 5

    /** Минимальный разрыв, о котором стоит говорить: меньше — это шум оценки. */
    private const val MIN_PROTEIN_GAP_G = 10.0
    private const val MIN_ENERGY_GAP_KCAL = 150.0
    private const val MIN_FIBER_GAP_G = 4.0

    /** Клетчатка на грамм продукта. */
    private const val FIBER_PER_G_VEGETABLES = 0.025
    private const val FIBER_PER_G_LEGUMES = 0.07

    /** Белковые продукты: граммы белка на 100 г продукта. */
    private data class Food(val name: String, val proteinPer100g: Double)

    private val PROTEIN_FOODS = listOf(
        Food("куриной грудки", 23.0),
        Food("творога 5%", 17.0),
        Food("тунца в собственном соку", 22.0),
        Food("греческого йогурта", 9.0),
    )

    fun build(report: DayReport): List<Advice> {
        // Пустой день: советов нет, экран показывает «нет готовых карточек».
        if (report.regimen.mealCount == 0) return emptyList()

        val items = mutableListOf<Advice>()

        val warnings = mutableListOf<Advice>()
        proteinAdvice(report)?.let { warnings += it }
        energyAdvice(report)?.let { warnings += it }
        fiberAdvice(report)?.let { warnings += it }
        sugarAdvice(report)?.let { warnings += it }
        ultraAdvice(report)?.let { warnings += it }
        lateAdvice(report)?.let { warnings += it }
        gapAdvice(report)?.let { warnings += it }
        mealCountAdvice(report)?.let { warnings += it }
        items += warnings

        weekAdvice(report)?.let { items += it }

        if (items.size < MAX_ITEMS) {
            if (warnings.isEmpty()) {
                items += goodDayAdvice(report)
            } else {
                proteinOkAdvice(report)?.let { items += it }
            }
        }
        return items.take(MAX_ITEMS)
    }

    // --- пункты отчёта ---

    private fun proteinAdvice(report: DayReport): Advice? {
        val gap = report.protein.gapG
        if (gap < MIN_PROTEIN_GAP_G || report.protein.verdict != NutrientVerdict.BELOW) return null
        val sources = proteinSources(gap)
        return Advice(
            id = "protein_low",
            tone = AdviceTone.WARN,
            title = "Белка не хватает",
            detail = "До цели не хватает ≈${grams(gap)} г белка — это $sources. " +
                "Добавьте белок в завтрак и ужин: он дольше держит сытость.",
        )
    }

    private fun energyAdvice(report: DayReport): Advice? {
        val gap = report.energy.gapKcal
        if (gap >= MIN_ENERGY_GAP_KCAL && report.energy.verdict == EnergyVerdict.LIKELY_DEFICIT) {
            return Advice(
                id = "energy_low",
                tone = AdviceTone.WARN,
                title = "Не хватает калорий",
                detail = "До цели ≈${kcal(gap)} ккал — добавьте перекус: ${snackExample(gap)}. " +
                    "Большой дефицит чаще срывает режим, чем ускоряет результат.",
            )
        }
        if (gap <= -MIN_ENERGY_GAP_KCAL && report.energy.verdict == EnergyVerdict.LIKELY_EXCESS) {
            return Advice(
                id = "energy_high",
                tone = AdviceTone.WARN,
                title = "Перебор калорий",
                detail = "Выше цели на ≈${kcal(-gap)} ккал — это десерт или лишняя порция гарнира. " +
                    "Следующий день начните с обычной порции, без «компенсации» голодом.",
            )
        }
        return null
    }

    private fun fiberAdvice(report: DayReport): Advice? {
        val gap = report.fiberGapG
        if (gap < MIN_FIBER_GAP_G || report.fiberVerdict != NutrientVerdict.BELOW) return null
        return Advice(
            id = "fiber_low",
            tone = AdviceTone.WARN,
            title = "Мало клетчатки",
            detail = "До цели ≈${grams(gap)} г клетчатки — это ${fiberMix(gap)}. " +
                "Овощи в каждый приём пищи закрывают разрыв без подсчётов.",
        )
    }

    private fun sugarAdvice(report: DayReport): Advice? {
        val over = report.sugarOverLimitG
        if (over <= 0 || report.sugarVerdict != LimitVerdict.ABOVE) return null
        val spoons = (over / 4.0).roundToInt().coerceAtLeast(1)
        return Advice(
            id = "sugar_high",
            tone = AdviceTone.WARN,
            title = "Сахар выше лимита",
            detail = "Выше лимита на ≈${grams(over)} г — примерно $spoons ч. л. сахара. " +
                "Замените сладкий напиток на воду или чай без сахара: это самый быстрый способ вернуться в лимит.",
        )
    }

    private fun ultraAdvice(report: DayReport): Advice? {
        val composition = report.composition
        if (composition.ultraProcessedItems == 0 || report.ultraProcessedShare < 0.3) return null
        return Advice(
            id = "ultra_processed",
            tone = AdviceTone.WARN,
            title = "Много ультрапереработанного",
            detail = "${composition.ultraProcessedItems} из ${composition.totalItems} компонентов — " +
                "снеки, сладости или полуфабрикаты. Замените один такой продукт на цельный: фрукт, орехи или йогурт.",
        )
    }

    private fun lateAdvice(report: DayReport): Advice? {
        val late = report.regimen.lateMeals
        if (late == 0) return null
        val last = report.regimen.lastIntake?.let { "%02d:%02d".format(it.hour, it.minute) }
        val lastPart = last?.let { ", последний в $it" }.orEmpty()
        return Advice(
            id = "late_meal",
            tone = AdviceTone.WARN,
            title = "Поздний ужин",
            detail = "${pluralRu(late, "приём", "приёма", "приёмов")} после ${ReportCalc.LATE_MEAL_HOUR}:00$lastPart. " +
                "Попробуйте сдвинуть ужин на 20:00–21:00: так проще не переедать вечером.",
        )
    }

    private fun gapAdvice(report: DayReport): Advice? {
        val minutes = report.regimen.longestGapMinutes ?: return null
        val hours = minutes / 60
        if (hours < 6) return null
        return Advice(
            id = "long_gap",
            tone = AdviceTone.TIP,
            title = "Длинный перерыв",
            detail = "$hours ч между приёмами. Добавьте перекус с белком — йогурт, творог или горсть орехов.",
        )
    }

    private fun mealCountAdvice(report: DayReport): Advice? {
        val meals = report.regimen.mealCount
        if (meals == 0 || meals >= ReportCalc.MIN_DONE_MEALS) return null
        return Advice(
            id = "meal_count",
            tone = AdviceTone.TIP,
            title = "Мало приёмов пищи",
            detail = "${pluralRu(meals, "готовый приём", "готовых приёма", "готовых приёмов")}. " +
                "Для ровного дня нужно три: завтрак, обед и ужин — иначе вечером тянет на сладкое.",
        )
    }

    private fun weekAdvice(report: DayReport): Advice? {
        val history = report.history
        val avg = history.avgKcal ?: return null
        if (history.daysWithData < 3) return null
        val target = report.energy.targetKcal
        val diff = target - avg
        val detail = when {
            diff > 150 -> "Среднее за ${history.daysWithData} дн. — ≈$avg ккал при цели $target: " +
                "ниже на ≈${kcal(diff.toDouble())} ккал в день. Дефицит держится стабильно, следите за белком."
            diff < -150 -> "Среднее за ${history.daysWithData} дн. — ≈$avg ккал при цели $target: " +
                "выше на ≈${kcal(-diff.toDouble())} ккал в день. Неделя важнее отдельного дня — стоит урезать перекусы."
            else -> "Среднее за ${history.daysWithData} дн. — ≈$avg ккал при цели $target: " +
                "неделя идёт ровно, отдельные дни можно не выравнивать."
        }
        return Advice(
            id = "week",
            tone = AdviceTone.TIP,
            title = "Итоги недели",
            detail = detail,
        )
    }

    private fun proteinOkAdvice(report: DayReport): Advice? {
        val protein = report.protein
        // «В норме» — только когда белка заведомо достаточно: весь интервал выше цели.
        // Иначе вердикт «не определить», и хвалить не за что.
        if (protein.verdict != NutrientVerdict.ABOVE) return null
        return Advice(
            id = "protein_ok",
            tone = AdviceTone.GOOD,
            title = "Белка достаточно",
            detail = "≈${grams(protein.interval.mid)} г при цели ${protein.targetG} г — так и держите: " +
                "белок в завтраке и ужине.",
        )
    }

    private fun goodDayAdvice(report: DayReport): Advice {
        val fiber = report.composition.fiberInterval.mid.roundToInt()
        return Advice(
            id = "day_ok",
            tone = AdviceTone.GOOD,
            title = "День без перекосов",
            detail = "≈${kcal(report.energy.midKcal)} ккал при цели ${report.energy.targetKcal}, " +
                "белок ≈${grams(report.protein.interval.mid)} г, клетчатка ≈$fiber г — явных перекосов нет.",
        )
    }

    // --- перевод чисел в продукты ---

    /** «≈195 г куриной грудки или ≈265 г творога»: первые два продукта, где порция разумна. */
    private fun proteinSources(gapG: Double): String {
        val parts = PROTEIN_FOODS.mapNotNull { food ->
            val need = gapG / food.proteinPer100g * 100
            if (need in 20.0..450.0) "≈${grams(need)} г ${food.name}" else null
        }.take(2)
        return if (parts.isEmpty()) "порция белкового продукта" else parts.joinToString(" или ")
    }

    private fun snackExample(gapKcal: Double): String = when {
        gapKcal < 250 -> "яблоко с горстью орехов (≈250 ккал)"
        gapKcal < 500 -> "творог 150 г с бананом (≈300 ккал)"
        else -> "омлет из двух яиц с овощами и цельнозерновой тост (≈450 ккал)"
    }

    /** Комбинация «овощи + бобовые», которая покрывает разрыв по клетчатке. */
    private fun fiberMix(gapG: Double): String {
        val vegetables = min(gapG / FIBER_PER_G_VEGETABLES, 400.0)
        val rest = gapG - vegetables * FIBER_PER_G_VEGETABLES
        if (rest < 1.0) return "≈${grams(vegetables, step = 50)} г овощей"
        val legumes = (rest / FIBER_PER_G_LEGUMES).coerceAtLeast(40.0)
        return "≈${grams(vegetables, step = 50)} г овощей и ≈${grams(legumes, step = 10)} г варёной чечевицы"
    }

    // --- мелочи форматирования ---

    private fun grams(value: Double, step: Int = 5): String =
        ((value / step).roundToInt() * step).coerceAtLeast(step).toString()

    private fun kcal(value: Double): String = ((value / 10.0).roundToInt() * 10).toString()
}
