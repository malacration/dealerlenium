package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.DealerProperties
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Selenide.`$`
import org.springframework.stereotype.Component

@Component
class HomePage(
    private val dealerProperties: DealerProperties,
) : AuthenticatedPage {

    override fun goHome(): HomePage {
        return this
    }
}
