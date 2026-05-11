package br.andrew.dealerlenium.infrastructure

import br.andrew.dealerlenium.infrastructure.configurations.KeycloakProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val keycloakProperties: KeycloakProperties,
    private val keycloakJwtRolesConverter: KeycloakJwtRolesConverter,
    private val corsConfiguration : CorsConfig
) {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(corsConfiguration.customizer)
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/", "/health").permitAll()
                    .requestMatchers(HttpMethod.POST, "/pix/share/verify").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }

        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val decoder = keycloakProperties.jwkSetUri
            ?.takeIf { it.isNotBlank() }
            ?.let { jwkSetUri ->
                NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
            }
            ?: JwtDecoders.fromIssuerLocation(keycloakProperties.issuerUri) as JwtDecoder

        val withIssuer = JwtValidators.createDefaultWithIssuer(keycloakProperties.issuerUri)
        val subjectValidator: OAuth2TokenValidator<Jwt> =
            JwtClaimValidator<String>(JwtClaimNames.SUB) { subject -> !subject.isNullOrBlank() }

        if (decoder is NimbusJwtDecoder) {
            decoder.setJwtValidator(DelegatingOAuth2TokenValidator(withIssuer, subjectValidator))
        }

        return decoder
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setPrincipalClaimName(keycloakProperties.principalAttribute)
        converter.setJwtGrantedAuthoritiesConverter(keycloakJwtRolesConverter)
        return converter
    }
}
