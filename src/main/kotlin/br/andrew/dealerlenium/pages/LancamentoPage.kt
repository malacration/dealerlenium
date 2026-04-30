package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.browser.BrowserRuntime
import com.codeborne.selenide.Condition.visible
import org.springframework.stereotype.Component

@Component
class LancamentoPage(
    private val homePage: HomePage,
    private val dealerProperties: DealerProperties,
) : AuthenticatedPage {
    override fun goHome(): HomePage = homePage.goHome()

    fun createDocument() {
        val selector =
            dealerProperties.navigation.createDocumentSelector.takeIf { it.isNotBlank() }
                ?: error("Propriedade obrigatoria ausente: dealer.navigation.create-document-selector")
        BrowserRuntime.css(selector).shouldBe(visible).click()
    }
}
