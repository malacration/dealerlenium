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

    fun textOrNullByIdContains(idFragment: String): String? {
        return textOrNull(selectorByAttributeContains("id", idFragment))
    }

    fun textOrNullByAnyIdContains(vararg idFragments: String): String? {
        return idFragments.asSequence()
            .map(::textOrNullByExactId)
            .firstOrNull { !it.isNullOrBlank() }
            ?: idFragments.asSequence()
            .map(::textOrNullByIdContains)
            .firstOrNull { !it.isNullOrBlank() }
    }

    fun isEnabled(selector: String): Boolean {
        val element = BrowserRuntime.css(selector)
        return element.exists() && element.getAttribute("disabled") == null
    }

    fun isChecked(selector: String): Boolean {
        val element = BrowserRuntime.css(selector)
        return element.exists() && element.isSelected
    }

    fun isCheckedByNameContains(nameFragment: String): Boolean {
        return isChecked(selectorByAttributeContains("name", nameFragment))
    }

    fun exists(selector: String): Boolean {
        return BrowserRuntime.css(selector).exists()
    }

    fun selectorByAnyIdContains(vararg idFragments: String): String {
        return idFragments.asSequence()
            .map(::selectorByExactId)
            .firstOrNull { exists(it) }
            ?: idFragments.asSequence()
            .map { selectorByAttributeContains("id", it) }
            .firstOrNull { exists(it) }
            ?: selectorByExactId(idFragments.first())
    }

    private fun textOrNullByExactId(id: String): String? {
        return textOrNull(selectorByExactId(id))
    }

    private fun selectorByExactId(id: String): String {
        val escapedId = id.replace("\\", "\\\\").replace("\"", "\\\"")
        return "[id=\"$escapedId\"]"
    }

    private fun selectorByAttributeContains(attribute: String, fragment: String): String {
        val escapedFragment = fragment.replace("\\", "\\\\").replace("'", "\\'")
        return "[$attribute*='$escapedFragment']"
    }
}
