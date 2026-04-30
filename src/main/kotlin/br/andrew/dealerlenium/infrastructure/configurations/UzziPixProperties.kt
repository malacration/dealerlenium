package br.andrew.dealerlenium.infrastructure.configurations

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "uzzipix")
data class UzziPixProperties(
    val baseUrl: String,
)
