package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.browser.BrowserRuntime
import com.codeborne.selenide.SelenideConfig
import com.codeborne.selenide.SelenideDriver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URL
import kotlin.test.assertEquals

class SelenideElementHelperTest {

    private var driver: SelenideDriver? = null

    @AfterEach
    fun tearDown() {
        driver?.close()
        driver = null
    }

    @Test
    fun `returns exact selector for html with vPESSOA_CODIGO using a single value`() {
        assertSelector(
            resourcePath = "/html/pessoa-codigo.html",
            expectedSelector = "[id=\"vPESSOA_CODIGO\"]",
            "vPESSOA_CODIGO",
        )
    }

    @Test
    fun `returns exact selector for html with vPESSOA_CODIGO when missing value comes first`() {
        assertSelector(
            resourcePath = "/html/pessoa-codigo.html",
            expectedSelector = "[id=\"vPESSOA_CODIGO\"]",
            "vPESSOA_CODIGOGRID",
            "vPESSOA_CODIGO",
        )
    }

    @Test
    fun `returns exact selector for html with vPESSOA_CODIGO when existing value comes first`() {
        assertSelector(
            resourcePath = "/html/pessoa-codigo.html",
            expectedSelector = "[id=\"vPESSOA_CODIGO\"]",
            "vPESSOA_CODIGO",
            "vPESSOA_CODIGOGRID",
        )
    }

    @Test
    fun `returns exact selector for html with vPESSOA_CODIGOGRID using a single value`() {
        assertSelector(
            resourcePath = "/html/pessoa-codigogrid.html",
            expectedSelector = "[id=\"vPESSOA_CODIGOGRID\"]",
            "vPESSOA_CODIGOGRID",
        )
    }

    @Test
    fun `returns exact selector for html with vPESSOA_CODIGOGRID when missing value comes first`() {
        assertSelector(
            resourcePath = "/html/pessoa-codigogrid.html",
            expectedSelector = "[id=\"vPESSOA_CODIGOGRID\"]",
            "vPESSOA_CODIGO",
            "vPESSOA_CODIGOGRID",
        )
    }

    @Test
    fun `returns exact selector for html with vPESSOA_CODIGOGRID when existing value comes first`() {
        assertSelector(
            resourcePath = "/html/pessoa-codigogrid.html",
            expectedSelector = "[id=\"vPESSOA_CODIGOGRID\"]",
            "vPESSOA_CODIGOGRID",
            "vPESSOA_CODIGO",
        )
    }

    private fun assertSelector(
        resourcePath: String,
        expectedSelector: String,
        vararg idFragments: String,
    ) {
        val selenideDriver = SelenideDriver(
            SelenideConfig()
                .browser("chrome")
                .headless(true)
        )
        driver = selenideDriver

        BrowserRuntime.withDriver(selenideDriver) {
            selenideDriver.open(resolveResource(resourcePath).toExternalForm())

            val selector = SelenideElementHelper.selectorByAnyIdContains(*idFragments)

            assertEquals(expectedSelector, selector)
        }
    }

    private fun resolveResource(path: String): URL {
        return checkNotNull(javaClass.getResource(path)) {
            "Recurso de teste nao encontrado: $path"
        }
    }
}
