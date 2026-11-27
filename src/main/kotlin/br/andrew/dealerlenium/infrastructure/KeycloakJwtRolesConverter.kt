package br.andrew.dealerlenium.infrastructure

import br.andrew.dealerlenium.infrastructure.configurations.KeycloakProperties
import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class KeycloakJwtRolesConverter(
    private val keycloakProperties: KeycloakProperties,
) : Converter<Jwt, Collection<GrantedAuthority>> {
    override fun convert(source: Jwt): Collection<GrantedAuthority> {
        return extractRoles(source)
            .map { SimpleGrantedAuthority(it) }
    }

    private fun extractRoles(jwt: Jwt): Set<String> {
        val realmRoles = extractRealmRoles(jwt)
        val resourceRoles = extractResourceRoles(jwt) { true }

        val roles = normalize(realmRoles + resourceRoles).toMutableSet()
        // Integracao (contas a pagar): role de qualquer client nao basta. So vale a role de realm ou dos
        // clients confiaveis, e so no token da service account de um client permitido (azp + preferred_username).
        roles.remove(INTEGRACAO_CONTAS_PAGAR)
        if (integracaoPermitida(jwt, realmRoles)) roles.add(INTEGRACAO_CONTAS_PAGAR)
        return roles
    }

    private fun integracaoPermitida(jwt: Jwt, realmRoles: List<String>): Boolean {
        val azp = jwt.getClaimAsString("azp")?.trim()
        if (azp.isNullOrEmpty() || azp !in keycloakProperties.integracaoClientIds) return false
        if (!serviceAccount(jwt, azp)) return false
        val clientes = keycloakProperties.integracaoRoleClients.toSet()
        val confiaveis = realmRoles + extractResourceRoles(jwt) { it in clientes }
        return INTEGRACAO_CONTAS_PAGAR in normalize(confiaveis)
    }

    /**
     * Token de service account do client [azp] (client_credentials), nao de usuario que logou por ele:
     * o Keycloak emite `preferred_username = service-account-<clientId>` (username reservado ao usuario da
     * service account) e, quando presente, `client_id`/`clientId` igual ao azp.
     */
    private fun serviceAccount(jwt: Jwt, azp: String): Boolean {
        val username = jwt.getClaimAsString("preferred_username")?.trim() ?: return false
        if (!username.equals(SERVICE_ACCOUNT_PREFIXO + azp, ignoreCase = true)) return false
        return listOf("client_id", "clientId").all { claim ->
            jwt.claims[claim]?.let { it.toString().trim() == azp } ?: true
        }
    }

    private fun normalize(roles: List<String>): Set<String> = roles
        .map { it.trim().uppercase() }
        .filter { it.isNotBlank() }
        .toSet()

    @Suppress("UNCHECKED_CAST")
    private fun extractRealmRoles(jwt: Jwt): List<String> {
        val realmAccess = jwt.claims["realm_access"] as? Map<String, Any?>
            ?: return emptyList()

        return (realmAccess["roles"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractResourceRoles(jwt: Jwt, client: (String) -> Boolean): List<String> {
        val resourceAccess = jwt.claims["resource_access"] as? Map<String, Any?>
            ?: return emptyList()

        return resourceAccess
            .filterKeys(client)
            .values
            .mapNotNull { it as? Map<String, Any?> }
            .flatMap { clientAccess ->
                (clientAccess["roles"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            }
    }

    companion object {
        const val INTEGRACAO_CONTAS_PAGAR = "INTEGRACAO_CONTAS_PAGAR"
        const val SERVICE_ACCOUNT_PREFIXO = "service-account-"
    }
}
