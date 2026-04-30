package br.andrew.dealerlenium.browser

import com.codeborne.selenide.Selenide
import com.codeborne.selenide.SelenideDriver
import com.codeborne.selenide.SelenideElement
import com.codeborne.selenide.SelenideTargetLocator
import com.codeborne.selenide.WebDriverRunner
import org.openqa.selenium.WebDriver

object BrowserRuntime {
    private val currentDriver = ThreadLocal<SelenideDriver?>()

    fun <T> withDriver(driver: SelenideDriver, action: () -> T): T {
        val previousDriver = currentDriver.get()
        currentDriver.set(driver)
        return try {
            action()
        } finally {
            if (previousDriver == null) {
                currentDriver.remove()
            } else {
                currentDriver.set(previousDriver)
            }
        }
    }

    fun css(selector: String): SelenideElement {
        return currentDriver.get()?.`$`(selector) ?: Selenide.`$`(selector)
    }

    fun xpath(selector: String): SelenideElement {
        return currentDriver.get()?.`$x`(selector) ?: Selenide.`$x`(selector)
    }

    fun open(url: String) {
        currentDriver.get()?.open(url) ?: Selenide.open(url)
    }

    fun getWebDriver(): WebDriver {
        return currentDriver.get()?.getWebDriver() ?: WebDriverRunner.getWebDriver()
    }

    fun switchTo(): SelenideTargetLocator {
        return currentDriver.get()?.switchTo() ?: Selenide.switchTo()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> executeJavaScript(script: String, vararg arguments: Any): T? {
        val driver = currentDriver.get()
        val jsArguments: Array<out Any> = arguments
        return if (driver != null) {
            driver.executeJavaScript<Any>(script, *jsArguments) as T?
        } else {
            Selenide.executeJavaScript<Any>(script, *jsArguments) as T?
        }
    }

    fun sleep(milliseconds: Long) {
        Selenide.sleep(milliseconds)
    }
}
