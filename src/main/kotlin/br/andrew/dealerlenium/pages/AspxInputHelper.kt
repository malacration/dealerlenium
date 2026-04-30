package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.browser.BrowserRuntime
import com.codeborne.selenide.Condition.visible
import org.openqa.selenium.Keys
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object AspxInputHelper {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val decimalSymbols = DecimalFormatSymbols(Locale.forLanguageTag("pt-BR")).apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }
    private val moneyFormatter = DecimalFormat("0.00", decimalSymbols)

    fun setDate(selector: String, date: LocalDate) {
        typeAndBlur(selector, dateFormatter.format(date))
    }

    fun setMoney(selector: String, value: BigDecimal) {
        typeAndBlur(selector, moneyFormatter.format(value))
    }

    private fun typeAndBlur(selector: String, value: String) {
        val element = BrowserRuntime.css(selector).shouldBe(visible)
        element.click()
        element.toWebElement().sendKeys(Keys.chord(Keys.CONTROL, "a"), value, Keys.TAB)
    }
}
