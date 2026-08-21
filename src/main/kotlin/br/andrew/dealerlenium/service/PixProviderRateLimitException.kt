package br.andrew.dealerlenium.service

import java.time.Duration

/**
 * O PSP recusou a consulta por excesso de requisicoes (HTTP 429).
 */
class PixProviderRateLimitException(
    val retryAfter: Duration? = null,
    message: String = "O banco esta limitando as consultas no momento.",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
