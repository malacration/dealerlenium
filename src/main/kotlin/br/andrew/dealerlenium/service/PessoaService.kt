package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.browser.BrowserRuntime
import br.andrew.dealerlenium.controller.PixClienteAdvanceRequest
import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.model.Cliente
import br.andrew.dealerlenium.model.PixPagamentoResponse
import br.andrew.dealerlenium.model.TransactionParameters
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.pages.SelenideElementHelper
import com.codeborne.selenide.Condition.visible
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class PessoaService(
    private val browserSessionManager: BrowserSessionManager,
    private val uzziPixService: UzziPixService,
    private val pixTransactionHistoryService: PixTransactionHistoryService,
    private val empresaProperties: EmpresaProperties,
) {

    fun getCliente(idCliente: Int): Cliente {
        return browserSessionManager.runInClonedStateDriver("wp_pessoaconsulta.aspx") { homePage ->
            BrowserRuntime.css("#vPESSOA_CODIGO").shouldBe(visible).setValue(idCliente.toString())
            BrowserRuntime.css("#IMGREFRESH").shouldBe(visible).click()
            homePage.waitAjaxLoadingToFinish()

            val codigo = SelenideElementHelper.textOrNull("#span_PESSOA_CODIGO_0001")
                ?.trim()
                ?.toIntOrNull()
                ?: throw IllegalArgumentException("Cliente $idCliente nao encontrado")

            Cliente(
                codigo = codigo,
                nome = SelenideElementHelper.textOrNull("#span_vPESSOA_NOMEGRID_0001"),
                nomeFantasia = SelenideElementHelper.textOrNull("#span_vPESSOA_NOMEFANTASIAGRID_0001"),
                cpfCnpj = SelenideElementHelper.textOrNull("#span_vPESSOA_DOCIDENTIFICADORGRID_0001"),
                municipio = SelenideElementHelper.textOrNull("#span_vPESSOAENDERECO_MUNICIPIONOM_0001"),
                uf = SelenideElementHelper.textOrNull("#span_vPESSOAENDERECO_ESTADOCOD_0001"),
                ativo = SelenideElementHelper.isChecked("input[name='PESSOA_ATIVO_0001']"),
                codigoMontadora = SelenideElementHelper.textOrNull("#span_PESSOA_CODIGOMONTADORA_0001"),
            )
        }
    }

    fun gerarPixAdiantamento(request: PixClienteAdvanceRequest): PixPagamentoResponse {
        val idCliente = request.idCliente.toIntOrNull()
            ?: throw IllegalArgumentException("ID do cliente invalido")
        val empresa = request.branchId.trim()
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Filial deve ser informada")
        val valor = request.valor
            ?: throw IllegalArgumentException("Valor do adiantamento deve ser informado")

        val cliente = getCliente(idCliente)
        empresaProperties.getEmpresaOrThrow(empresa)
        val retorno = uzziPixService.createPixByEmpresa(
            empresa,
            gerarTxId(),
            valor,
        )
        val expiracao = retorno.data.expiresAt
            ?: (retorno.data.generatedAt ?: OffsetDateTime.now(ZoneOffset.UTC)).plusHours(12)
        val nomeCliente = cliente.nome ?: cliente.nomeFantasia ?: "Cliente $idCliente"

        val pixPagamentoResponse = PixPagamentoResponse(
            idLancamento = idCliente,
            tipoTransacao = TipoTransacao.ADIANTAMENTO,
            status = "ADIANTAMENTO",
            valor = valor,
            descricao = "Adiantamento cliente $idCliente - Empresa $empresa",
            nomeRecebedor = nomeCliente,
            expiracaoEm = DateTimeFormatter.ISO_INSTANT.format(expiracao.toInstant()),
            retornoPix = retorno,
        )

        val documento = pixTransactionHistoryService.salvarHistoricoPixAdiantamento(
            empresa = empresa,
            cliente = cliente,
            pixPagamentoResponse = pixPagamentoResponse,
            retornoPix = retorno,
            parametros = TransactionParameters(
                idCliente = request.idCliente,
                branchId = request.branchId,
                valor = valor,
                departamentoCod = request.departamentoCod,
                tipoFichaRazaoCod = request.tipoFichaRazaoCod,
            ),
        )

        return pixPagamentoResponse.also {
            it.documento = documento
        }
    }

    private fun gerarTxId(): String = "PIX-${UUID.randomUUID().toString().substring(0, 12).uppercase()}"
}
