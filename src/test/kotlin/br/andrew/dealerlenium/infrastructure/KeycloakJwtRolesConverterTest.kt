package br.andrew.dealerlenium.infrastructure

import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class KeycloakJwtRolesConverterTest {
    private val converter = KeycloakJwtRolesConverter()

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
