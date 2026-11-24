package ru.fitapp.ai

/**
 * Детальный исход проверки ключа (§9 PLAN, экран «Онбординг: ключ»):
 * UI обязан показывать понятную ошибку, включая отдельную подсказку про TLS-сертификат.
 */
sealed interface KeyProbeResult {
    /** Ключ принят провайдером и qwen4-max присутствует в каталоге моделей. */
    data object Valid : KeyProbeResult

    /** Провайдер отверг ключ (401/403). */
    data class InvalidKey(val message: String) : KeyProbeResult

    /** Ключ принят, но qwen4-max в подписке нет (§12 PLAN: витрина и модели расходятся). */
    data object ModelMissing : KeyProbeResult

    /** Ошибка проверки сертификата: на устройстве нет доверенного CA провайдера (§11 PLAN). */
    data class TlsError(val message: String) : KeyProbeResult

    /** Сеть недоступна или провайдер ответил неожиданное. */
    data class NetworkError(val message: String) : KeyProbeResult
}
