package ru.fitapp.ai

import ru.fitapp.domain.AnalyzeOutcome

/**
 * Единственная точка, знающая про провайдера (§5 PLAN).
 * QwenAnalyzer и MockAnalyzer подменяются в тестах без сети.
 */
interface DishAnalyzer {
    /** Разбор одного кадра. Один кадр = один запрос (§4.4). */
    suspend fun analyze(jpeg: ByteArray): AnalyzeOutcome

    /** Проверка ключа в онбординге: GET /models и наличие qwen4-max (§9). */
    suspend fun probeKey(): Boolean

    /**
     * Проверка ключа с различимыми исходами — для понятных ошибок в UI (§9, §12):
     * неверный ключ, отсутствие qwen4-max, TLS-ошибка, сеть.
     */
    suspend fun probeKeyDetailed(): KeyProbeResult
}
