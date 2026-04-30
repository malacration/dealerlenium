package br.andrew.dealerlenium.repositorys

import br.andrew.dealerlenium.model.TransactionDocument
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface TransactionRepository : MongoRepository<TransactionDocument, String> {
    fun findAllByStatus(status: String): List<TransactionDocument>
    fun findFirstByTxIdOrderByCreatedAtDesc(txId: String): TransactionDocument?
    fun findFirstByIdLancamentoAndPixExpiraEmAfterOrderByCreatedAtDesc(
        idLancamento: Int,
        pixExpiraEm: Instant,
    ): TransactionDocument?

    fun getByIdLancamento(idLancamento: Int) : TransactionDocument?
    fun findByTxId(txId: String) : TransactionDocument?
}
