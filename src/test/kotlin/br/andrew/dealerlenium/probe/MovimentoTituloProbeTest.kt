package br.andrew.dealerlenium.probe

import br.andrew.dealerlenium.browser.BrowserRuntime
import br.andrew.dealerlenium.pages.FrameSwitcher
import br.andrew.dealerlenium.pages.NavigationPage
import br.andrew.dealerlenium.repositorys.TransactionRepository
import br.andrew.dealerlenium.service.BrowserSessionManager
import br.andrew.dealerlenium.service.OpenTransactionSettlementProcessor
import br.andrew.dealerlenium.service.PixPagamentoService
import br.andrew.dealerlenium.service.PixTransactionHistoryService
import br.andrew.dealerlenium.service.UzziPixService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.nio.file.Files
import java.nio.file.Path

/**
 * SONDA DESCARTAVEL (nao commitar): abre a tela de movimentacao de um titulo a receber
 * e grava o HTML de todos os frames + os <select> encontrados. Nao confirma nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dealer-e2e-pipeline")
@Tag("dealer-probe")
class MovimentoTituloProbeTest {
    @Autowired private lateinit var sessions: BrowserSessionManager
    @Autowired private lateinit var nav: NavigationPage
    @Autowired private lateinit var frames: FrameSwitcher

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder
    @MockitoBean private lateinit var transactionRepository: TransactionRepository
    @MockitoBean private lateinit var pixPagamentoService: PixPagamentoService
    @MockitoBean private lateinit var openTransactionSettlementProcessor: OpenTransactionSettlementProcessor
    @MockitoBean private lateinit var uzziPixService: UzziPixService
    @MockitoBean private lateinit var pixTransactionHistoryService: PixTransactionHistoryService

    private val out: Path = Path.of(System.getenv("PROBE_OUT") ?: "build/probe").also { Files.createDirectories(it) }
    private val titulo = (System.getenv("PROBE_TITULO") ?: "1107684").toInt()
    private val empresa = System.getenv("PROBE_EMPRESA") ?: "AUTOVEMA_PVH"

    @Test
    fun movimentoTitulo() {
        sessions.runInSession(empresa) { home ->
            val page = nav.goContasReceber(home)
            dump("01-contas-receber")
            frames.switchToFrameBySrc("menucontarecebertitulo.aspx", fromRoot = true)
            val registro = runCatching { page.getContasReceberByCodigo(titulo) }
            log("registro: $registro")
            dump("02-grid")
            frames.switchToFrameBySrc("menucontarecebertitulo.aspx", fromRoot = true)
            if (registro.isFailure) return@runInSession
            BrowserRuntime.css("#vBMPMOVIMENTO_0001").click()
            Thread.sleep(6000)
            dump("03-movimento")
            frames.switchToFrameBySrc("menucontarecebertitulo.aspx", fromRoot = true)
            frames.switchToFrameBySrc("wp_titulomov.aspx")
            BrowserRuntime.css("#INSERT").click()
            Thread.sleep(6000)
            dump("04-insert")
            escolher("select[id*='TIPOCD' i]:not([id*='RECEB' i])", "recebimento de t")
            dump("05-tipocd")
            visiveis("apos-tipocd")
            escolher("#TITULOMOV_AGENTECOBRADORTIPO, #vAGENTECOBRADORTIPO", "banco")
            visiveis("apos-tipo-banco")
            escolher("#TITULOMOV_AGENTECOBRADORCOD, #vAGENTECOBRADORCOD", "uzzipay")
            visiveis("apos-agente")
            dump("06-agente")
            escolher("#TITULOMOV_TIPODOCUMENTOCOD", "aviso de lanc")
            dump("07-tipodoc")
        }
    }

    @Test
    fun contaGerencialLookup() {
        sessions.runInSession(empresa) { home ->
            val lanc = nav.goLacamentoPage(home)
            BrowserRuntime.css("#IMAGECGIDENTIFICADOR").click()
            frames.switchToFrameBySrc("sel_contagerencialidentificadorporempresa.aspx")
            org.openqa.selenium.support.ui.Select(BrowserRuntime.getWebDriver().findElement(By.id("vFILTRO_EMPRESACOD"))).selectByValue("1")
            lanc.waitAjaxLoadingToFinish(); Thread.sleep(3000)
            dumpFrame("cg-01-filtros")
            for ((rotulo, css, valor) in listOf(
                Triple("ident", "#vFILTRO_CONTAGERENCIAL_IDENTIFICADOR", "30212001"),
                Triple("desc", "#vFILTRO_CONTAGERENCIAL_DESCRICAO", "UZZIPAY"),
            )) {
                val driver = BrowserRuntime.getWebDriver()
                driver.findElements(By.cssSelector("input[id^='vFILTRO'][type='text']")).forEach { runCatching { it.clear() } }
                val campo = driver.findElements(By.cssSelector(css)).firstOrNull { it.isDisplayed }
                if (campo == null) { log("lookup: campo $css nao encontrado"); continue }
                campo.clear(); campo.sendKeys(valor, org.openqa.selenium.Keys.TAB)
                lanc.waitAjaxLoadingToFinish(); Thread.sleep(4000)
                dumpFrame("cg-02-$rotulo")
            }
        }
    }

    /** Salva o frame atual e o texto das linhas do grid, sem trocar de frame. */
    private fun dumpFrame(label: String) {
        val driver = BrowserRuntime.getWebDriver()
        Files.writeString(out.resolve("$label.html"), driver.pageSource, Charsets.UTF_8)
        val inputs = driver.findElements(By.cssSelector("input[type='text'], select")).filter { it.isDisplayed }
            .joinToString(" | ") { "${it.getAttribute("id")}=${it.getAttribute("value")}" }
        val linhas = driver.findElements(By.cssSelector("tr[id^='Grid'], tr[class*='Row' i], tr[class*='Odd' i], tr[class*='Even' i]"))
            .map { it.text.replace("\n", " ").trim() }.filter { it.isNotBlank() }.take(40)
        log("=== $label\n  campos: $inputs\n" + linhas.joinToString("\n") { "  row: $it" })
    }

    /** Procura o select em qualquer frame e escolhe a opcao cujo texto contem [texto]. Nao confirma nada. */
    private fun escolher(css: String, texto: String) {
        val driver = BrowserRuntime.getWebDriver()
        fun find(path: List<Int>): Boolean {
            driver.switchTo().defaultContent()
            path.forEach { driver.switchTo().frame(it) }
            val sel = driver.findElements(By.cssSelector(css)).firstOrNull { it.isDisplayed }
            if (sel != null) {
                val select = org.openqa.selenium.support.ui.Select(sel)
                val opt = select.options.firstOrNull { it.text.lowercase().contains(texto) }
                log("escolher $css em frame $path: id=${sel.getAttribute("id")} opcao=${opt?.getAttribute("value")}=${opt?.text}")
                if (opt != null) { select.selectByValue(opt.getAttribute("value")!!); Thread.sleep(5000) }
                return true
            }
            val count = driver.findElements(By.cssSelector("iframe, frame")).size
            return (0 until count).any { find(path + it) }
        }
        if (!find(emptyList())) log("escolher: nenhum select visivel para $css")
        driver.switchTo().defaultContent()
    }

    private fun visiveis(label: String) {
        val driver = BrowserRuntime.getWebDriver()
        driver.switchTo().defaultContent()
        driver.switchTo().frame(1)
        driver.switchTo().frame(0)
        val itens = driver.findElements(By.cssSelector("input:not([type=hidden]), select, img[onclick], a[onclick]"))
            .filter { runCatching { it.isDisplayed }.getOrDefault(false) }
            .joinToString("\n") { e ->
                val v = if (e.tagName == "select") org.openqa.selenium.support.ui.Select(e).firstSelectedOption.text else e.getAttribute("value")
                "  ${e.tagName}#${e.getAttribute("id")} = $v"
            }
        log("--- visiveis $label\n$itens")
        driver.switchTo().defaultContent()
    }

    private fun log(msg: String) {
        println("PROBE $msg")
        Files.writeString(out.resolve("log.txt"), msg + "\n", Charsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
    }

    /** Percorre todos os frames a partir da raiz, salvando HTML e resumo dos selects. */
    private fun dump(label: String) {
        val driver = BrowserRuntime.getWebDriver()
        val handleFrames = mutableListOf<String>()
        fun walk(path: List<Int>) {
            driver.switchTo().defaultContent()
            path.forEach { driver.switchTo().frame(it) }
            val name = (listOf(label) + path.map { "f$it" }).joinToString("_")
            val url = (driver as JavascriptExecutor).executeScript("return location.href") as String
            Files.writeString(out.resolve("$name.html"), "<!-- $url -->\n" + driver.pageSource, Charsets.UTF_8)
            val selects = driver.findElements(By.tagName("select")).joinToString("\n") { sel ->
                val opts = sel.findElements(By.tagName("option")).joinToString(" | ") {
                    "${it.getAttribute("value")}=${it.getAttribute("textContent")?.trim()}"
                }
                "  select#${sel.getAttribute("id")}: $opts"
            }
            handleFrames += "$name  $url\n$selects"
            val count = driver.findElements(By.cssSelector("iframe, frame")).size
            for (i in 0 until count) walk(path + i)
        }
        walk(emptyList())
        driver.switchTo().defaultContent()
        log("=== $label\n" + handleFrames.joinToString("\n"))
    }
}
