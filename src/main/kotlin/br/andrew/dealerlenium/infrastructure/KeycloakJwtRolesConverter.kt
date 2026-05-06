package br.andrew.dealerlenium.infrastructure

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class KeycloakJwtRolesConverter : Converter<Jwt, Collection<GrantedAuthority>> {
    override fun convert(source: Jwt): Collection<GrantedAuthority> {
        return extractRoles(source)
            .map { SimpleGrantedAuthority(it) }
    }

    private fun extractRoles(jwt: Jwt): Set<String> {
        val realmRoles = extractRealmRoles(jwt)
        val resourceRoles = extractResourceRoles(jwt)

        return (realmRoles + resourceRoles)
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractRealmRoles(jwt: Jwt): List<String> {
        val realmAccess = jwt.claims["realm_access"] as? Map<String, Any?>
            ?: return emptyList()

        return realmAccess["roles"] as? List<String> ?: emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractResourceRoles(jwt: Jwt): List<String> {
        val resourceAccess = jwt.claims["resource_access"] as? Map<String, Any?>
            ?: return emptyList()

        return resourceAccess.values
            .mapNotNull { it as? Map<String, Any?> }
            .flatMap { clientAccess ->
                clientAccess["roles"] as? List<String> ?: emptyList()
            }
    }
}

