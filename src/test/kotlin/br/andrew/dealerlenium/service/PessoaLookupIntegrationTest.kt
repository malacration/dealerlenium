package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.model.Cliente
import br.andrew.dealerlenium.repositorys.TransactionRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dealer-e2e-pipeline")
class PessoaLookupIntegrationTest {

    @Autowired
    private lateinit var pessoaService: PessoaService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var transactionRepository: TransactionRepository

    @MockitoBean
    private lateinit var pixPagamentoService: PixPagamentoService

    @MockitoBean
    private lateinit var openTransactionSettlementProcessor: OpenTransactionSettlementProcessor

    @MockitoBean
    private lateinit var uzziPixService: UzziPixService

    @MockitoBean
    private lateinit var pixTransactionHistoryService: PixTransactionHistoryService

    @Test
    fun buscaPessoaPorId123625() {
        val cliente = pessoaService.getCliente(123625)

        assertClienteBasico(cliente)
        assertEquals(123625, cliente.codigo)
    }

    private fun assertClienteBasico(cliente: Cliente) {
        assertTrue(
            !cliente.nome.isNullOrBlank() || !cliente.nomeFantasia.isNullOrBlank(),
            "Esperava nome ou nome fantasia preenchido para o cliente consultado",
        )
    }
}
