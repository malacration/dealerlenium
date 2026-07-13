package br.andrew.dealerlenium.controller

import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.model.TransactionStatus
import br.andrew.dealerlenium.repositorys.TransactionRepository
import br.andrew.dealerlenium.service.OpenTransactionSettlementProcessor
import br.andrew.dealerlenium.service.UsuarioEmpresaService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Validated
@RestController
@RequestMapping("/api/transactions")
class TransactionController(
    private val transactionRepository: TransactionRepository,
    private val usuarioEmpresaService: UsuarioEmpresaService,
    private val openTransactionSettlementProcessor: OpenTransactionSettlementProcessor,
) {

    @GetMapping
    fun listarTodas(authentication: Authentication): List<TransactionDocument> {
        return transactionRepository.findAllByEmpresaIn(
            usuarioEmpresaService.getEmpresaIds(authentication),
        )
    }

    @GetMapping("/{id}")
    fun buscarPorId(
        @PathVariable id: String,
        authentication: Authentication,
    ): TransactionDocument {
        val transaction = transactionRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction com id '$id' nao encontrada.")
        }

        if (!usuarioEmpresaService.hasAccessToTransaction(authentication, transaction)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction com id '$id' nao encontrada.")
        }

        return transaction
    }

    @GetMapping("/status/{status}")
    fun buscarPorStatus(
        @PathVariable status: String,
        authentication: Authentication,
    ): List<TransactionDocument> {
        val transactionStatus = TransactionStatus.from(status)
        return transactionRepository.findAllByStatusAndEmpresaIn(
            transactionStatus.value,
            usuarioEmpresaService.getEmpresaIds(authentication),
        )
    }

    @PostMapping("/{id}/retry-settlement")
    fun retentarBaixa(
        @PathVariable id: String,
        authentication: Authentication,
    ): TransactionDocument {
        val transaction = buscarPorId(id, authentication)
        if (transaction.status != TransactionStatus.ERRO_BAIXA) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "A transaction com id '$id' esta com status '${transaction.status.value}'. Somente transactions com status '${TransactionStatus.ERRO_BAIXA.value}' podem ter baixa retentada.",
            )
        }

        openTransactionSettlementProcessor.retrySettlement(id)
        return buscarPorId(id, authentication)
    }
}
