package br.andrew.dealerlenium.infrastructure

import br.andrew.dealerlenium.infrastructure.configurations.KeycloakProperties
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeycloakJwtRolesConverterTest {
    private val converter = KeycloakJwtRolesConverter(KeycloakProperties(issuerUri = "https://sso.example/realms/rovema"))

    // Mesma montagem do SecurityConfig: a authority que /api/contas-pagar/** exige.
    private fun integracao(claims: Map<String, Any>): Boolean = JwtAuthenticationConverter()
        .apply { setJwtGrantedAuthoritiesConverter(converter) }
        .convert(jwt(claims))!!.authorities.any { it.authority == KeycloakJwtRolesConverter.INTEGRACAO_CONTAS_PAGAR }

    private fun clientRole(client: String, vararg roles: String) =
        mapOf("resource_access" to mapOf(client to mapOf("roles" to roles.toList())))

    private fun sa(client: String) = mapOf("azp" to client, "preferred_username" to "service-account-$client", "client_id" to client)

    @Test
    fun `service account do Nota Agil com client role do sapvema recebe integracao`() {
        assertTrue(integracao(sa("nota-agil-integracao") + clientRole("sapvema", "integracao_contas_pagar")))
        assertTrue(integracao(sa("nota-agil-integracao") + ("realm_access" to mapOf("roles" to listOf("integracao_contas_pagar")))))
        // Keycloak antigo: clientId; username sem diferenciar maiusculas.
        assertTrue(integracao(mapOf("azp" to "nota-agil-integracao", "preferred_username" to "Service-Account-Nota-Agil-Integracao",
            "clientId" to "nota-agil-integracao") + clientRole("sapvema", "integracao_contas_pagar")))
    }

    @Test
    fun `usuario que logou pelo client permitido nao recebe integracao`() {
        val role = clientRole("sapvema", "integracao_contas_pagar")
        assertFalse(integracao(mapOf("azp" to "nota-agil-integracao", "preferred_username" to "fulano") + role))
        assertFalse(integracao(mapOf("azp" to "nota-agil-integracao") + role), "sem preferred_username")
        // Service account de outro client com azp forjado nao confere.
        assertFalse(integracao(mapOf("azp" to "nota-agil-integracao", "preferred_username" to "service-account-outro-client") + role))
        assertFalse(integracao(sa("nota-agil-integracao") + ("client_id" to "outro-client") + role))
    }

    @Test
    fun `role de mesmo nome em client nao relacionado nao concede integracao`() {
        assertFalse(integracao(sa("nota-agil-integracao") + clientRole("outro-client", "integracao_contas_pagar")))
        assertFalse(integracao(sa("outro-client") + clientRole("outro-client", "integracao_contas_pagar")))
        // Client nao permitido, mesmo com a role no client confiavel.
        assertFalse(integracao(sa("outro-client") + clientRole("sapvema", "integracao_contas_pagar")))
    }

    @Test
    fun `token de usuario comum nao recebe integracao mesmo com a role`() {
        val usuario = mapOf("azp" to "pix-portal", "preferred_username" to "fulano",
            "realm_access" to mapOf("roles" to listOf("integracao_contas_pagar", "admin"))) +
            clientRole("sapvema", "integracao_contas_pagar")
        assertFalse(integracao(usuario))
        // As demais roles continuam valendo.
        assertTrue(converter.convert(jwt(usuario)).any { it.authority == "ADMIN" })
        assertFalse(integracao(clientRole("sapvema", "integracao_contas_pagar")), "sem azp")
    }

    @Test
    fun `converts debug realm role to expected authority`() {
        val jwt = jwt(
            mapOf(
                "realm_access" to mapOf(
                    "roles" to listOf("debug"),
                ),
            ),
        )

        assertEquals(setOf("DEBUG"), converter.convert(jwt).map { it.authority }.toSet())
    }

    @Test
    fun `converts debug resource role to expected authority`() {
        val jwt = jwt(
            mapOf(
                "resource_access" to mapOf(
                    "dealerlenium" to mapOf(
                        "roles" to listOf("DeBuG"),
                    ),
                ),
            ),
        )

        assertEquals(setOf("DEBUG"), converter.convert(jwt).map { it.authority }.toSet())
    }

    private fun jwt(claims: Map<String, Any>): Jwt {
        val now = Instant.now()
        return Jwt(
            "token",
            now,
            now.plusSeconds(60),
            mapOf("alg" to "none"),
            mapOf("sub" to "user") + claims,
        )
    }
}
