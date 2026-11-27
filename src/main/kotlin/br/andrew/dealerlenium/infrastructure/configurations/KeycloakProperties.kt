package br.andrew.dealerlenium.infrastructure.configurations

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keycloak")
data class KeycloakProperties(
    val issuerUri: String,
    val jwkSetUri: String? = null,
    val principalAttribute: String = "preferred_username",
    /**
     * Clients (claim `azp`) cujas service accounts podem receber a authority INTEGRACAO_CONTAS_PAGAR.
     * Token de usuario comum (azp do front) ou de outro client nao recebe, mesmo com a role.
     */
    val integracaoClientIds: List<String> = listOf("nota-agil-integracao"),
    /** Clients cujas client roles valem para INTEGRACAO_CONTAS_PAGAR (alem da role de realm). */
    val integracaoRoleClients: List<String> = listOf("sapvema"),
)

