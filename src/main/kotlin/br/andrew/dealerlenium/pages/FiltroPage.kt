package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.browser.BrowserRuntime
import com.codeborne.selenide.Condition.exist
import com.codeborne.selenide.Condition.visible
import org.springframework.stereotype.Component

@Component
class FiltroPage(
    val homePage: HomePage
) : AuthenticatedPage {

    override fun goHome(): HomePage {
        return homePage
    }

    fun selecionarFiltro(tipoFiltro: String, valorFiltro: String): FiltroPage {
        BrowserRuntime.xpath("//table[starts-with(@id,'GridselecaoContainerTbl')]//a[contains(normalize-space(.),'${tipoFiltro}')]")
            .shouldBe(exist).click()
        waitAjaxLoadingToFinish()
        BrowserRuntime.css("#BTNDESMARCARTODOS").shouldBe(visible).click()
        waitAjaxLoadingToFinish()
        BrowserRuntime.xpath("//table[starts-with(@id,'TABLEFILTROS')]//option[contains(normalize-space(.),'${valorFiltro}')]")
            .shouldBe(exist).click()
        BrowserRuntime.css("#BTNMARCARITEM").shouldBe(visible).click()
        waitAjaxLoadingToFinish()
        return this
    }

    fun selecionarAll(tipoFiltro: String): FiltroPage {
        BrowserRuntime.xpath("//table[starts-with(@id,'GridselecaoContainerTbl')]//a[contains(normalize-space(.),'${tipoFiltro}')]")
            .shouldBe(exist).click()
        waitAjaxLoadingToFinish()
        BrowserRuntime.css("#BTNMARCARTODOS").shouldBe(visible).click()
        waitAjaxLoadingToFinish()
        return this;
    }
}
