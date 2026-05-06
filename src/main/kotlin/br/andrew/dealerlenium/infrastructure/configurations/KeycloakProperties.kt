package br.andrew.dealerlenium.infrastructure.configurations

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keycloak")
data class KeycloakProperties(
    val issuerUri: String,
    val jwkSetUri: String? = null,
    val principalAttribute: String = "preferred_username",
)

