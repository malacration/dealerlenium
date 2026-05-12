package br.andrew.dealerlenium.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@ConfigurationProperties(prefix = "cors")
data class CorsProperties(val origins: List<String> = emptyList())

@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class CorsConfig(private val corsProperties: CorsProperties) {

    private val allowedOriginPatterns: List<String> get() = mutableListOf(
        "http://localhost:[*]",
        "http://localhost:4200",
        "http://*.localhost:[*]",
        "http://172.18.30.147:4200",
    ).also {
        it.addAll(corsProperties.origins.filter { origin -> origin.isNotBlank() })
    }

    private val allowedMethods = listOf("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")

    private val allowedHeaders = listOf(
        "Authorization",
        "Cache-Control",
        "Content-Type",
        "cache",
        "pragma",
        "traceparent",
        "tracestate",
        "Origin",
        "Accept",
        "X-Requested-With",
    )

    private val exposedHeaders = listOf(
        "Authorization",
        "error",
        "arquivo",
        "info",
        "cache",
        "Content-Type",
    )

    @Bean
    fun corsConfigurationSource(): UrlBasedCorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", getCorsConfig())
        return source
    }

    @Bean
    fun webMvcConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**")
                    .allowedOriginPatterns(*allowedOriginPatterns.toTypedArray())
                    .allowedMethods(*allowedMethods.toTypedArray())
                    .allowedHeaders(*allowedHeaders.toTypedArray())
                    .exposedHeaders(*exposedHeaders.toTypedArray())
                    .allowCredentials(true)
                    .maxAge(3600)
            }
        }
    }

    fun getCorsConfig(): CorsConfiguration {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = allowedOriginPatterns
        configuration.allowedMethods = allowedMethods
        configuration.allowedHeaders = allowedHeaders
        configuration.exposedHeaders = exposedHeaders
        configuration.allowCredentials = true
        configuration.maxAge = 3600
        return configuration
    }

    var customizer: Customizer<CorsConfigurer<HttpSecurity>> = Customizer { cors ->
        cors.configurationSource(corsConfigurationSource())
    }
}
