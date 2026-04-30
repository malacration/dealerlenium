package br.andrew.dealerlenium.controller

import br.andrew.dealerlenium.model.PixPagamentoResponse
import br.andrew.dealerlenium.model.PixShareVerificationRequest
import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.repositorys.TransactionRepository
import br.andrew.dealerlenium.service.AdiantamentoService
import br.andrew.dealerlenium.service.PessoaService
import br.andrew.dealerlenium.service.PixPagamentoService
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.jvm.optionals.getOrNull

@Validated
@RestController
@RequestMapping("/pix")
class PixController(
    private val pixPagamentoService: PixPagamentoService,
    private val adiantamentoService : AdiantamentoService,
    private val pessoaService: PessoaService,
    private val repository : TransactionRepository
) {

    @GetMapping("/contas-receber/{idLancamento}")
    fun gerarPixPagamentoByContasReceber(
        @PathVariable @Positive idLancamento: Int,
        @RequestParam(defaultValue = "false") forcarNovoPix: Boolean,
    ): PixPagamentoResponse = pixPagamentoService.gerarPixPagamento(idLancamento, forcarNovoPix)

    @PostMapping("/adiantamento")
    fun gerarPixPagamentoByAdiantamento(
        @RequestBody request: PixClienteAdvanceRequest,
    ): PixPagamentoResponse {

        val retorno  = pessoaService.gerarPixAdiantamento(request)
        adiantamentoService.baixaAdiantamento(retorno.documento)
        return retorno
    }

    @PostMapping("/share/verify")
    fun verifySharedPix(
        @RequestBody request: PixShareVerificationRequest,
    ): PixTransactionConsultationResponse = pixPagamentoService.verificarPixCompartilhado(request)


    @GetMapping("/transaction/{id}")
    fun getTransaction(
        @PathVariable id: String
    ): PixTransactionConsultationResponse{
        //TODO arrumar esse construtor para teste
        val pagamento = PixTransactionConsultationResponse(
            txId = id,
            tipoTransacao = TipoTransacao.ADIANTAMENTO,
            originalAmount = BigDecimal("3000"),
            paid = true,
            paymentDate = DateTimeFormatter.ISO_DATE.format(LocalDateTime.now()),
            paymentType = "Payment",
            receivedAmount = BigDecimal("3000"),
            status = "Criado",
            valor = BigDecimal("3000"),
            descricao = "Adiantamento cliente 123625 - Empresa AUTOVEMA_PVH",
            nomeRecebedor = "FRANCISCO MARCELLO DA SILVA RIBEIRO",
            nomeFavorecido = "Autovema Porto Velho",
            qrCode = "00020101021226990014br.gov.bcb.pix2577pix-h.bancogenial.com/32192325/qrs1/v2/01LCpxA27uLc9tlVbBc0V9Zur3pjMsrwua8ClG52040000530398654073000.005802BR5920SUSTENNUTRI N ANIMAL6009SAO PAULO62070503***63046C0D",
            qrCodeBase64 = "https://pix-h.bancogenial.com/32192325/qrs1/v2/01LCpxA27uLc9tlVbBc0V9Zur3pjMsrwua8ClG",
            expiracaoEm = "2026-04-16T09:36:14.491Z",
        )
        val transactionDocument = repository.findById(id).orElseThrow { throw Exception("Adiantamento não foi encontrado") }
        adiantamentoService.baixaAdiantamento(transactionDocument )
        return pagamento
//        return pixPagamentoService.consultarPagamentoDaTransacao(id)
    }
}

data class PixClienteAdvanceRequest(
    @field:NotBlank
    val idCliente: String,
    @field:NotBlank
    val branchId: String,
    val valor: BigDecimal?,
)
