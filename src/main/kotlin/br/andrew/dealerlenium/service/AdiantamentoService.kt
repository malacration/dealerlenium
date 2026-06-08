package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.browser.BrowserRuntime
import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.pages.AspxInputHelper
import br.andrew.dealerlenium.pages.AuthenticatedPage
import br.andrew.dealerlenium.pages.FrameSwitcher
import br.andrew.dealerlenium.pages.NavigationPage
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.Condition.disappear
import org.openqa.selenium.By
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.ZoneId

@Service
class AdiantamentoService(
    private val browserSessionManager: BrowserSessionManager,
    private val nav : NavigationPage,
    private val empresaProperties: EmpresaProperties,
    private val frameSwitcher: FrameSwitcher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun baixaAdiantamento(transaction: TransactionDocument, pagamento: PixTransactionConsultationResponse): Int? {
        return browserSessionManager.runInClonedStateDriver { session ->
            val empresa = empresaProperties.getEmpresaOrThrow(transaction.empresa)
            val adiantamento = empresa.adiantamento
            session.changeFilial(empresa)
            val lancamentoPage = nav.goLacamentoPage(session)
            val clienteCodigo = transaction.clienteCodigo
                ?: throw IllegalArgumentException("Cliente codigo nao informado para o adiantamento ${transaction.id ?: transaction.txId}")
            val dataLancamento = transaction.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()

            BrowserRuntime.css("#IMAGECGIDENTIFICADOR").shouldBe(visible).click()
            frameSwitcher.switchToFrameBySrc("sel_contagerencialidentificadorporempresa.aspx")
            BrowserRuntime.css("#vFILTRO_CONTAGERENCIAL_IDENTIFICADOR")
                .shouldBe(visible)
                .setValue(adiantamento.contaGerencialIdentificador)

            lancamentoPage.waitAjaxLoadingToFinish()
            frameSwitcher.afterCloseCurrentFrameSwitchToParent({
                BrowserRuntime.css("#vLINKSELECTION_0001").shouldBe(visible).click()
            })

            BrowserRuntime.css("#INSERT").shouldBe(visible).click()
            lancamentoPage.waitAjaxLoadingToFinish()
            BrowserRuntime.css("#vTESOURARIA_TIPOCDCOD")
                .shouldBe(visible)
                .selectOptionByValue(adiantamento.tipoCodigo)
            lancamentoPage.waitAjaxLoadingToFinish()
            BrowserRuntime.css("#vTESOURARIA_TIPODOCUMENTOCOD")
                .shouldBe(visible)
                .selectOptionByValue(adiantamento.tipoDocumentoCodigo)
            lancamentoPage.waitAjaxLoadingToFinish()
            val parametros = transaction.parametros
            val departamentoCod = parametros?.departamentoCod
            if (departamentoCod != null) {
                BrowserRuntime.css("#CTLTESOURARIA_DEPARTAMENTOCOD")
                    .shouldBe(visible)
                    .selectOptionByValue(departamentoCod.toString())
            } else {
                BrowserRuntime.css("#CTLTESOURARIA_DEPARTAMENTOCOD")
                    .shouldBe(visible)
                    .selectOption(adiantamento.departamento)
            }
            lancamentoPage.waitAjaxLoadingToFinish()
            val tipoFichaRazaoCod = parametros?.tipoFichaRazaoCod
            if (tipoFichaRazaoCod != null) {
                BrowserRuntime.css("#vTESOURARIA_TIPOFICHARAZAOCOD")
                    .shouldBe(visible)
                    .selectOptionByValue(tipoFichaRazaoCod.toString())
            } else {
                BrowserRuntime.css("#vTESOURARIA_TIPOFICHARAZAOCOD")
                    .shouldBe(visible)
                    .selectOption(adiantamento.tipoFichaRazao)
            }
            AspxInputHelper.setDate("#vTESOURARIA_DATACAIXA", dataLancamento)
            AspxInputHelper.setDate("#vTESOURARIA_DATAMOVIMENTO", dataLancamento)
            BrowserRuntime.css("#vPESSOA_CODIGO")
                .shouldBe(visible)
                .setValue(clienteCodigo)
            BrowserRuntime.css("#CTLTESOURARIA_OBSERVACAO")
                .shouldBe(visible)
                .setValue(buildObservacao(transaction))
            AspxInputHelper.setMoney("#vTESOURARIA_VALOR", pagamento.valor ?: throw Exception("Não tem valor no pagamento"))

            BrowserRuntime.css("#CTLTESOURARIA_NRODOCUMENTO")
                .takeIf { it.exists() }
                ?.setValue(transaction.reference.substring(0,14))

            BrowserRuntime.css("#CTLTESOURARIA_NSU")
                .takeIf { it.exists() } //se existe e esta ativo e visivel
                ?.setValue(transaction.reference.substring(0,14))

            return@runInClonedStateDriver finalizarBaixa(session)
        }
    }

    private fun finalizarBaixa(session: AuthenticatedPage): Int? {
        var numero : Int? = null
        frameSwitcher.afterCloseCurrentFrameSwitchToParent({
            BrowserRuntime.css("#BTNCONFIRMAR").shouldBe(visible).click()
            numero = capturarResultadoDaBaixa(session)
        })
        return numero
    }

    private fun capturarResultadoDaBaixa(session: AuthenticatedPage): Int? {
        val reciboAberto = frameSwitcher.trySwitchToFrameBySrc("wp_reciboemite.aspx", timeoutSeconds = 10)
        if (!reciboAberto) {
            val erroAplicacao = resolveApplicationErrorMessage()
            if (!erroAplicacao.isNullOrBlank()) {
                throw Exception("Erro ao realizar baixa no ERP! Mensagem: $erroAplicacao")
            }

            session.waitAjaxLoadingToFinish()
            val erroAposAjax = resolveApplicationErrorMessage()
            if (!erroAposAjax.isNullOrBlank()) {
                throw Exception("Erro ao realizar baixa no ERP! Mensagem: $erroAposAjax")
            }

            throw Exception("Erro ao realizar baixa no ERP! O popup do recibo nao foi aberto.")
        }

        val numeroRecibo = extractReceiptId()
        frameSwitcher.switchToParentFrameWhenReady()
        closeReceiptPopup()
        return numeroRecibo
    }

    private fun extractReceiptId(): Int? {
        val reciboText = currentGxErrorViewerText()
            .ifBlank { parentGxErrorViewerText() }

        return Regex("\\d+")
            .find(reciboText)
            ?.value
            ?.toInt()
            ?.also { receiptId ->
                logger.info("ID do recibo capturado: {}", receiptId)
            }
    }

    private fun resolveApplicationErrorMessage(): String? {
        val confirmPanelText = BrowserRuntime.getWebDriver()
            .findElements(By.cssSelector("#DVELOP_CONFIRMPANELContainer .Body"))
            .mapNotNull { it.text?.trim() }
            .firstOrNull { it.isNotBlank() }
        if (!confirmPanelText.isNullOrBlank()) {
            return confirmPanelText
        }

        val gxErrorViewerText = currentGxErrorViewerText()
        if (gxErrorViewerText.isBlank()) {
            return null
        }

        return gxErrorViewerText.takeUnless { it.contains("Gravado com Sucesso", ignoreCase = true) }
    }

    private fun currentGxErrorViewerText(): String {
        return BrowserRuntime.getWebDriver()
            .findElements(By.cssSelector("#gxErrorViewer div"))
            .joinToString(" | ") { it.text.trim() }
            .trim()
    }

    private fun parentGxErrorViewerText(): String {
        return runCatching {
            frameSwitcher.switchToParentFrameWhenReady()
            currentGxErrorViewerText()
        }.getOrDefault("").also {
            runCatching {
                frameSwitcher.trySwitchToFrameBySrc("wp_reciboemite.aspx", timeoutSeconds = 2)
            }
        }
    }

    private fun closeReceiptPopup() {
        val closeButton = BrowserRuntime.css("#gxp0_cls")
        if (closeButton.exists()) {
            closeButton.shouldBe(visible).click()
            BrowserRuntime.css("#gxp0_b").should(disappear)
        }
    }

    private fun buildObservacao(transaction: TransactionDocument): String {
        return buildString {
            append("Baixa automatica de adiantamento PIX ref ")
            append(transaction.reference)
            append(" para cliente ")
            append(transaction.clienteNome)
            append(" no valor de ")
            append(transaction.valor)
            transaction.usuarioGeracao?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { usuario ->
                    append(". Cobrança gerada por ")
                    append(usuario)
                }
            append(".")
        }
    }
}
