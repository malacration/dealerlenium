package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.browser.BrowserRuntime
import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.pages.AspxInputHelper
import br.andrew.dealerlenium.pages.FrameSwitcher
import br.andrew.dealerlenium.pages.NavigationPage
import com.codeborne.selenide.Condition.visible
import org.springframework.stereotype.Service
import java.time.ZoneId

@Service
class AdiantamentoService(
    private val browserSessionManager: BrowserSessionManager,
    private val nav : NavigationPage,
    private val empresaProperties: EmpresaProperties,
    private val frameSwitcher: FrameSwitcher,
) {
    fun baixaAdiantamento(transaction: TransactionDocument, pagamento: PixTransactionConsultationResponse){
        browserSessionManager.runInClonedStateDriver { session ->
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
//            BrowserRuntime.css("#CTLTESOURARIA_NRODOCUMENTO")
//                .takeIf { it.exists() } //se existe e esta ativo e visivel
//                ?.setValue("123")

            frameSwitcher.afterCloseCurrentFrameSwitchToParent({
                BrowserRuntime.css("#BTNCONFIRMAR").shouldBe(visible).click()
            })
        }
    }

    private fun buildObservacao(transaction: TransactionDocument): String {
        return "Baixa automatica de adiantamento PIX ref ${transaction.reference} txId ${transaction.txId} " +
            "para cliente ${transaction.clienteNome} no valor de ${transaction.valor}."
    }
}
