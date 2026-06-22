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

    /**
     * Espera (por conteudo, nao por timing) ate que o texto de algum dos elementos
     * identificados por [idFragments] seja igual a [expected], ou ate o timeout.
     * Retorna o ultimo texto observado (ja trimado), que o chamador deve validar:
     * vazio/null = nao encontrado; diferente de [expected] = leitura suja/defasada.
     */
    fun waitForTextByAnyIdContains(
        expected: String,
        vararg idFragments: String,
        timeoutMs: Long = 8000,
        pollMs: Long = 100,
    ): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = textOrNullByAnyIdContains(*idFragments)?.trim()
        while (last != expected && System.currentTimeMillis() < deadline) {
            BrowserRuntime.sleep(pollMs)
            last = textOrNullByAnyIdContains(*idFragments)?.trim()
        }
        return last
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
