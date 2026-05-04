package br.andrew.dealerlenium.controller

import br.andrew.dealerlenium.model.PixPagamentoResponse
import br.andrew.dealerlenium.model.PixShareVerificationRequest
import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
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
        return pessoaService.gerarPixAdiantamento(request)
    }

    @PostMapping("/share/verify")
    fun verifySharedPix(
        @RequestBody request: PixShareVerificationRequest,
    ): PixTransactionConsultationResponse = pixPagamentoService.verificarPixCompartilhado(request)


    @GetMapping("/transaction/{id}")
    fun getTransaction(
        @PathVariable id: String
    ): PixTransactionConsultationResponse{
        val transactionDocument = repository.findById(id).orElseThrow { throw Exception("Adiantamento não foi encontrado") }
        val pagamento = pixPagamentoService.consultarPagamentoDaTransacao(transactionDocument.reference)
        if(pagamento.paid)
            adiantamentoService.baixaAdiantamento(transactionDocument,pagamento)
        return pagamento
    }
}

data class PixClienteAdvanceRequest(
    @field:NotBlank
    val idCliente: String,
    @field:NotBlank
    val branchId: String,
    val valor: BigDecimal?,
)
