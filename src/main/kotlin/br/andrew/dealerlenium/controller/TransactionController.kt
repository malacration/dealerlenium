package br.andrew.dealerlenium.controller

import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.repositorys.TransactionRepository
import br.andrew.dealerlenium.service.UsuarioEmpresaService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Validated
@RestController
@RequestMapping("/api/transactions")
class TransactionController(
    private val transactionRepository: TransactionRepository,
    private val usuarioEmpresaService: UsuarioEmpresaService,
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
        return transactionRepository.findAllByStatusAndEmpresaIn(
            status,
            usuarioEmpresaService.getEmpresaIds(authentication),
        )
    }
}
