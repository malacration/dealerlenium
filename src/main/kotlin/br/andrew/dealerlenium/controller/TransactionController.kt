package br.andrew.dealerlenium.controller

import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.repositorys.TransactionRepository
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

@Validated
@RestController
@RequestMapping("/api/transactions")
class TransactionController(
    private val transactionRepository: TransactionRepository,
) {

    @GetMapping
    fun listarTodas(): List<TransactionDocument> {
        return transactionRepository.findAll()
    }

    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: String): TransactionDocument {
        return transactionRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction com id '$id' nao encontrada.")
        }
    }

    @GetMapping("/status/{status}")
    fun buscarPorStatus(@PathVariable status: String): List<TransactionDocument> {
        return transactionRepository.findAllByStatus(status)
    }
}
