package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.browser.BrowserDebugArtifacts
import br.andrew.dealerlenium.browser.BrowserRuntime
import br.andrew.dealerlenium.infrastructure.configurations.Empresa
import com.codeborne.selenide.SelenideElement
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Condition.hidden
import java.time.Duration

interface AuthenticatedPage {

    companion object {
        /**
         * Botao da filial na barra da direita do portal: dentro da toolbar
         * (`x-toolbar-ct` / `x-toolbar-right-ct`), na linha `x-toolbar-right-row`, e o
         * **ultimo** botao com seta de menu (`em.x-btn-arrow`) que nao e o de
         * "Minimizadas" (esse tem id estavel `W5-minimized-menu`).
         *
         * O `last()` importa: dependendo do login vem antes o menu do usuario e outros
         * botoes com seta, e o da filial fica sempre por ultimo antes do "Minimizadas".
         *
         * Nao da para ancorar por posicao nem por id: os ids do ExtJS sao gerados
         * (`ext-genNN`) e mudam com o layout que o login recebe - o antigo `#ext-gen83`
         * chegou a cair numa estrela de favoritos, e a leitura da filial voltava vazia,
         * fazendo a sessao tentar trocar de filial clicando no elemento errado.
         */
        /** Candidatos a botao da filial, em ordem de documento. */
        const val XPATH_CANDIDATOS_FILIAL =
            "//tr[contains(concat(' ',normalize-space(@class),' '),' x-toolbar-right-row ')]" +
                "//table[contains(concat(' ',normalize-space(@class),' '),' x-btn ')][not(@id='W5-minimized-menu')]" +
                "//em[contains(concat(' ',normalize-space(@class),' '),' x-btn-arrow ')]//button"

        const val XPATH_FILIAL = "($XPATH_CANDIDATOS_FILIAL)[last()]"

        /**
         * Ultimo recurso: o id gerado pelo ExtJS. Em varios logins ele e mesmo o botao
         * da filial, mas nao da para confiar nele como primario - noutro layout esse id
         * caiu numa estrela de favoritos. So entra quando a estrutura da toolbar nao
         * resolve.
         */
        const val XPATH_FILIAL_FALLBACK = "//*[@id='ext-gen83']"

        /** Candidato (ou o fallback) cujo texto e a filial [nome] - confere sem saber qual botao e qual. */
        fun xpathFilialChamada(nome: String): String {
            val comTexto = "[normalize-space(.)='${nome.trim()}']"
            return "$XPATH_CANDIDATOS_FILIAL$comTexto | $XPATH_FILIAL_FALLBACK$comTexto"
        }
    }

    fun goHome(): HomePage

    fun changeFilial(empresa : Empresa): AuthenticatedPage = changeFilial(empresa.marca, empresa.id)

    /**
     * Botao para abrir o menu de filial: o ultimo candidato da toolbar ([XPATH_FILIAL])
     * e, se a estrutura nao bater, o id gerado pelo ExtJS ([XPATH_FILIAL_FALLBACK]).
     */
    fun elementoFilial(): SelenideElement {
        val porEstrutura = BrowserRuntime.xpath(XPATH_FILIAL)
        return if (porEstrutura.exists()) porEstrutura else BrowserRuntime.xpath(XPATH_FILIAL_FALLBACK)
    }

    /** Filial em que a sessao esta, lida do cabecalho do portal. */
    fun filialAtual(): String = elementoFilial().shouldBe(visible).text().trim()

    /**
     * A sessao ja esta na filial [nome]? Confere por texto entre todos os candidatos,
     * sem depender de qual botao do cabecalho e o da filial neste layout.
     */
    fun estaNaFilial(nome: String): Boolean = BrowserRuntime.xpath(xpathFilialChamada(nome)).exists()

    fun changeFilial(marca : String, empresaNome : String): AuthenticatedPage {
        if (!estaNaFilial(empresaNome)) {
            val filialAtual = filialAtual()
            elementoFilial().shouldBe(visible).click()
            waitAjaxLoadingToFinish()
            abrirItemDoMenu("//li[.//span[contains(normalize-space(.),'${marca}')]]", "marca '$marca'", filialAtual, empresaNome) {
                it.hover()
            }
            abrirItemDoMenu("//li[.//span[contains(normalize-space(.),'${empresaNome}')]]", "filial '$empresaNome'", filialAtual, empresaNome) {
                it.click()
            }
        }
        BrowserRuntime.xpath(xpathFilialChamada(empresaNome)).shouldBe(visible)
        return this
    }

    /**
     * O menu de troca de filial so mostra o que o login enxerga. Quando o item nao
     * aparece, o erro cru do Selenide ("elemento nao encontrado") nao diz se o menu nem
     * abriu ou se aquela credencial nao tem acesso aquela marca/filial - entao aqui o
     * diagnostico (filial atual + itens visiveis no menu) vai junto da excecao.
     */
    private fun abrirItemDoMenu(
        xpath: String,
        descricao: String,
        filialAtual: String,
        empresaNome: String,
        acao: (SelenideElement) -> Unit,
    ) {
        try {
            acao(BrowserRuntime.xpath(xpath).shouldBe(visible))
        } catch (error: Throwable) {
            val opcoes = itensVisiveisDoMenu()
            BrowserDebugArtifacts.captureCurrentContext("change-filial-$empresaNome")
            throw FilialIndisponivelException(
                "Nao foi possivel selecionar a $descricao ao trocar para a filial '$empresaNome'. " +
                    "Filial atual: '$filialAtual'. Itens visiveis no menu: $opcoes",
                error,
            )
        }
    }

    /** Textos dos itens de menu visiveis, para dizer o que aquele login enxerga. */
    private fun itensVisiveisDoMenu(): List<String> {
        return runCatching {
            BrowserRuntime.executeJavaScript<List<String>>(
                """
                return Array.from(document.querySelectorAll('li'))
                    .filter(li => li.offsetParent !== null)
                    .map(li => (li.innerText || '').trim())
                    .filter(texto => texto.length > 0 && texto.length < 80);
                """.trimIndent(),
            ).orEmpty()
        }.getOrDefault(emptyList())
    }

    fun waitAjaxLoadingToFinish(
        timeoutSeconds: Long = 10,
        appearWindowMs: Long = 400,
        pollMs: Long = 50,
    ): AuthenticatedPage {
        val deadline = System.currentTimeMillis() + appearWindowMs
        var appeared = isAjaxNotificationVisible()
        while (!appeared && System.currentTimeMillis() < deadline) {
            BrowserRuntime.sleep(pollMs)
            appeared = isAjaxNotificationVisible()
        }
        if (appeared) {
            try {
                BrowserRuntime.css("#gx_ajax_notification").shouldBe(hidden, Duration.ofSeconds(timeoutSeconds))
            } catch (error: Throwable) {
                BrowserDebugArtifacts.captureCurrentContext("ajax-timeout")
                throw error
            }
        }
        return this
    }

    private fun isAjaxNotificationVisible(): Boolean {
        return BrowserRuntime.executeJavaScript<Boolean>(
            """
            const el = document.getElementById('gx_ajax_notification');
            if (!el) return false;
            const style = window.getComputedStyle(el);
            return style.display !== 'none' && style.visibility !== 'hidden' && el.offsetParent !== null;
            """.trimIndent(),
        ) == true
    }
}

/** O menu de filial nao ofereceu a marca/filial pedida para o login da sessao. */
class FilialIndisponivelException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
