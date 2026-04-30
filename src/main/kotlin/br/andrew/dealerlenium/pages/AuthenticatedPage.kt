package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.browser.BrowserRuntime
import br.andrew.dealerlenium.infrastructure.configurations.Empresa
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Condition.exactText
import com.codeborne.selenide.Condition.hidden
import java.time.Duration

interface AuthenticatedPage {

    fun goHome(): HomePage

    fun changeFilial(empresa : Empresa): AuthenticatedPage = changeFilial(empresa.marca, empresa.id)

    fun changeFilial(marca : String, empresaNome : String): AuthenticatedPage {
        val filialAtual = BrowserRuntime.css("#ext-gen83").shouldBe(visible).text().trim()
        if (filialAtual != empresaNome.trim()) {
            BrowserRuntime.css("#ext-gen83").click()
            BrowserRuntime.xpath("//li[.//span[contains(normalize-space(.),'${marca}')]]").shouldBe(visible).hover()
            BrowserRuntime.xpath("//li[.//span[contains(normalize-space(.),'${empresaNome}')]]").shouldBe(visible).click()
        }
        BrowserRuntime.css("#ext-gen83").shouldBe(visible).shouldHave(exactText(empresaNome))
        return this
    }

    fun waitAjaxLoadingToFinish(
        timeoutSeconds: Long = 10,
        appearWindowMs: Long = 400,
        pollMs: Long = 50,
    ): AuthenticatedPage {
        val deadline = System.currentTimeMillis() + appearWindowMs
        var appeared = isAjaxNotificationVisible()
        while (!appeared && System.currentTimeMillis() < deadline) {
            BrowserRuntime.sleep(pollMs)
            appeared = isAjaxNotificationVisible()
        }
        if (appeared) {
            BrowserRuntime.css("#gx_ajax_notification").shouldBe(hidden, Duration.ofSeconds(timeoutSeconds))
        }
        return this
    }

    private fun isAjaxNotificationVisible(): Boolean {
        return BrowserRuntime.executeJavaScript<Boolean>(
            """
            const el = document.getElementById('gx_ajax_notification');
            if (!el) return false;
            const style = window.getComputedStyle(el);
            return style.display !== 'none' && style.visibility !== 'hidden' && el.offsetParent !== null;
            """.trimIndent(),
        ) == true
    }
}
