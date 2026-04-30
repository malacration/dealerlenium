package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.browser.BrowserRuntime

object SelenideElementHelper {
    fun textOrNull(selector: String): String? {
        val element = BrowserRuntime.css(selector)
        if (!element.exists()) {
            return null
        }

        val text = element.text.trim()
        return text.ifEmpty { null }
    }

    fun isEnabled(selector: String): Boolean {
        val element = BrowserRuntime.css(selector)
        return element.exists() && element.getAttribute("disabled") == null
    }

    fun isChecked(selector: String): Boolean {
        val element = BrowserRuntime.css(selector)
        return element.exists() && element.isSelected
    }

    fun exists(selector: String): Boolean {
        return BrowserRuntime.css(selector).exists()
    }
}
