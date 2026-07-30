package br.andrew.dealerlenium

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.mock.env.MockEnvironment
import java.time.Duration
import kotlin.test.assertEquals

class DealerPropertiesBindingTest {
    @Test
    fun `binds global selenide timeout from dealer properties`() {
        val properties = bindDealerProperties(
            "dealer.selenide-timeout" to "35s",
        )

        assertEquals(Duration.ofSeconds(35), properties.selenideTimeout)
    }

    @Test
    fun `uses ten seconds as default global selenide timeout`() {
        val properties = bindDealerProperties()

        assertEquals(Duration.ofSeconds(10), properties.selenideTimeout)
    }

    private fun bindDealerProperties(vararg extraProperties: Pair<String, String>): DealerProperties {
        val environment = MockEnvironment()
            .withProperty("dealer.login-url", "https://dealer.example/LoginAux.aspx")
            .withProperty("dealer.index-url", "https://dealer.example/Portal/default.html")
            .withProperty("dealer.tipo-titulo", "PIX QR-CODE UZZIPAY")

        extraProperties.forEach { (key, value) ->
            environment.withProperty(key, value)
        }

        return Binder.get(environment)
            .bind("dealer", DealerProperties::class.java)
            .get()
    }
}
