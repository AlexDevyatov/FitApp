package ru.fitapp.ai

import ru.fitapp.domain.AnalysisResult
import ru.fitapp.domain.AnalyzedItem
import ru.fitapp.domain.AnalyzeOutcome
import ru.fitapp.domain.FrameKind

/**
 * Детерминированные ответы для тестов без сети (§14 PLAN).
 * Конструктор принимает последовательность исходов; вызовы отдают их по порядку,
 * последний исход повторяется для всех последующих вызовов.
 */
class MockAnalyzer(
    private val outcomes: List<AnalyzeOutcome>,
    private val keyValid: Boolean = true,
    /** Готовый ответ текстового пересчёта; null — детерминированная оценка по названию. */
    private val estimateOutcome: AnalyzeOutcome? = null,
) : DishAnalyzer {

    constructor(outcome: AnalyzeOutcome) : this(listOf(outcome))

    init {
        require(outcomes.isNotEmpty()) { "MockAnalyzer требует хотя бы один исход" }
    }

    private var callIndex = 0

    /** Сколько раз запрашивали пересчёт по названию — для проверок в тестах. */
    var estimateCalls: Int = 0
        private set

    override suspend fun analyze(jpeg: ByteArray): AnalyzeOutcome {
        val outcome = outcomes[callIndex.coerceAtMost(outcomes.lastIndex)]
        callIndex++
        return outcome
    }

    /** Оценка по названию без сети: детерминированная, чтобы тесты пересчёта были стабильны. */
    override suspend fun estimate(name: String, grams: Double?): AnalyzeOutcome {
        estimateCalls++
        estimateOutcome?.let { return it }
        val portion = grams ?: DEFAULT_ESTIMATE_GRAMS
        return AnalyzeOutcome.Ok(
            AnalysisResult(
                kind = FrameKind.DISH,
                confidence = 0.5,
                notes = "тестовая оценка по названию",
                items = listOf(
                    AnalyzedItem(
                        name = name,
                        grams = portion,
                        kcal = portion * 1.5,
                        proteinG = portion * 0.08,
                        fatG = portion * 0.05,
                        carbsG = portion * 0.15,
                        fiberG = portion * 0.01,
                        sugarG = portion * 0.02,
                        ultraProcessed = false,
                    ),
                ),
            ),
        )
    }

    override suspend fun probeKey(): Boolean = keyValid

    override suspend fun probeKeyDetailed(): KeyProbeResult =
        if (keyValid) KeyProbeResult.Valid else KeyProbeResult.InvalidKey("Ключ неверен или просрочен")

    companion object {
        /** Масса по умолчанию для оценки по названию, когда текущая неизвестна. */
        const val DEFAULT_ESTIMATE_GRAMS = 200.0

        /** Сценарий «одно блюдо»: гречка с курицей. */
        fun dish(): AnalyzeOutcome = AnalyzeOutcome.Ok(
            AnalysisResult(
                kind = FrameKind.DISH,
                confidence = 0.82,
                notes = "Вес соуса оценён примерно",
                items = listOf(
                    AnalyzedItem("Гречка отварная", 200.0, 220.0, 8.0, 1.5, 42.0, 6.0, 0.8, false),
                    AnalyzedItem("Куриная грудка запечённая", 150.0, 250.0, 45.0, 6.0, 0.0, 0.0, 0.0, false),
                ),
            ),
        )

        /** Сценарий «тарелка с несколькими компонентами». */
        fun plate(): AnalyzeOutcome = AnalyzeOutcome.Ok(
            AnalysisResult(
                kind = FrameKind.PLATE,
                confidence = 0.74,
                notes = "Часть гарнира скрыта",
                items = listOf(
                    AnalyzedItem("Рис отварной", 180.0, 210.0, 4.5, 0.6, 45.0, 1.2, 0.1, false),
                    AnalyzedItem("Котлета говяжья", 120.0, 300.0, 22.0, 20.0, 8.0, 1.0, 3.0, true),
                    AnalyzedItem("Салат из овощей", 100.0, 60.0, 1.5, 3.0, 7.0, 2.5, 4.0, false),
                ),
            ),
        )

        /** Сценарий «напиток»: кофе без отдельно добавленного сахара. */
        fun drink(): AnalyzeOutcome = AnalyzeOutcome.Ok(
            AnalysisResult(
                kind = FrameKind.DRINK,
                confidence = 0.88,
                notes = "Объём чашки оценён примерно, сахар не учтён",
                items = listOf(
                    AnalyzedItem("Кофе чёрный", 200.0, 4.0, 0.2, 0.0, 0.6, 0.0, 0.0, false),
                ),
            ),
        )

        /** Сценарий «этикетка упаковки»: grams — оценка веса упаковки. */
        fun label(): AnalyzeOutcome = AnalyzeOutcome.Ok(
            AnalysisResult(
                kind = FrameKind.LABEL,
                confidence = 0.91,
                notes = "Данные взяты из таблицы состава",
                items = listOf(
                    AnalyzedItem("Йогурт питьевой 2,5%", 290.0, 180.0, 8.7, 7.3, 20.3, 0.0, 14.5, true),
                ),
            ),
        )

        /** Сценарий «не еда». */
        fun notFood(): AnalyzeOutcome = AnalyzeOutcome.NotFood("На фото виден стол и телефон, еды нет")

        /** Сценарий «неверный ключ». */
        fun authError(): AnalyzeOutcome = AnalyzeOutcome.AuthError("Ключ неверен или просрочен")

        /** Сценарий «лимит провайдера». */
        fun retryable(): AnalyzeOutcome = AnalyzeOutcome.Retryable("Лимит запросов (429)", retryAfterMs = 2000)

        /** Сценарий «битый запрос». */
        fun permanent(): AnalyzeOutcome = AnalyzeOutcome.Permanent("Провайдер отклонил запрос (400)")
    }
}
