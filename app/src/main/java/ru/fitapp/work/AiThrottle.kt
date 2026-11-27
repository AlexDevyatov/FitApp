package ru.fitapp.work

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Общий ограничитель запросов к провайдеру (§10 PLAN): не больше двух одновременных.
 * Один на процесс, поэтому его делят разбор снимков и пересчёт КБЖУ по названию —
 * иначе серия правок вперемешку со снимками ловит 429.
 */
object AiThrottle {

    const val MAX_CONCURRENT = 2

    private val semaphore = Semaphore(MAX_CONCURRENT)

    suspend fun <T> withPermit(block: suspend () -> T): T = semaphore.withPermit { block() }
}