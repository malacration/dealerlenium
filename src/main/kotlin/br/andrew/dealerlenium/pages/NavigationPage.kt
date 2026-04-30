package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.browser.BrowserRuntime
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.SelenideElement
import org.springframework.stereotype.Component

@Component
class NavigationPage(
    private val dealerProperties: DealerProperties,
    private val contasReceberPage: ContasReceberPage,
    private val lancamentoPage: LancamentoPage,
    private val frameSwitcher: FrameSwitcher,
)  {
    fun goHome(fromPage: AuthenticatedPage): HomePage = fromPage.goHome()

    fun goContasReceber(fromPage: AuthenticatedPage): ContasReceberPage {
        fromPage.waitAjaxLoadingToFinish()
        clickRequired(dealerProperties.navigation.financeiroSelector)
        hoverRequired(dealerProperties.navigation.contasReceberHoverSelector)
        clickRequired(dealerProperties.navigation.titulosReceberSelector)
        frameSwitcher.switchToFrameBySrc("menucontarecebertitulo.aspx")
        return contasReceberPage
    }

    fun goLacamentoPage(fromPage: AuthenticatedPage): LancamentoPage {
        fromPage.waitAjaxLoadingToFinish()
        clickRequired(dealerProperties.navigation.financeiroSelector)
        hoverRequired(dealerProperties.navigation.tesourariaHoverSelector)
        clickRequired(dealerProperties.navigation.lancamentosSelector)
        frameSwitcher.switchToFrameBySrc("wp_tesourariaselecao.aspx")
        return lancamentoPage
    }

    private fun clickRequired(selector: String) {
        findElement(selector).shouldBe(visible).click()
    }

    private fun hoverRequired(selector: String) {
        findElement(selector).shouldBe(visible).hover()
    }

    private fun findElement(selector: String): SelenideElement {
        return if (selector.startsWith("xpath=", ignoreCase = true)) {
            BrowserRuntime.xpath(selector.substringAfter("=", ""))
        } else {
            BrowserRuntime.css(selector)
        }
    }
}
