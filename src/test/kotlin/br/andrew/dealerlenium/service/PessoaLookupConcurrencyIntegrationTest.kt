package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.repositorys.TransactionRepository
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regressao para o vazamento de contexto entre buscas concorrentes (Francisco/Sabrina).
 *
 * Dispara buscas concorrentes de dois ids distintos repetidas vezes. Com o pool de
 * sessoes independentes (2 credenciais no profile hmg => pool de tamanho 2), cada
 * busca roda em uma sessao de servidor propria e nao deve receber o registro da outra.
 *
 * Opt-in: requer Chrome + acesso ao Dealer de homologacao. Habilite com
 * `RUN_DEALER_PESSOA_CONCURRENCY_TEST=true` e informe os dois ids de teste em
 * `DEALER_TEST_PESSOA_ID_A` (default 123625) e `DEALER_TEST_PESSOA_ID_B`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dealer-e2e-pipeline")
@Tag("dealer-pessoa-lookup")
@EnabledIfEnvironmentVariable(named = "RUN_DEALER_PESSOA_CONCURRENCY_TEST", matches = "true")
class PessoaLookupConcurrencyIntegrationTest {

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
    fun buscasConcorrentesNaoSeContaminam() {
        val idA = envInt("DEALER_TEST_PESSOA_ID_A", 123625)
        val idB = envInt("DEALER_TEST_PESSOA_ID_B", 0)
        assumeTrue(idB != 0 && idB != idA) {
            "Defina DEALER_TEST_PESSOA_ID_B com um id valido e distinto de DEALER_TEST_PESSOA_ID_A"
        }
        val rounds = envInt("DEALER_TEST_PESSOA_CONCURRENCY_ROUNDS", 5)

        val pool = Executors.newFixedThreadPool(2)
        val errors = CopyOnWriteArrayList<Throwable>()
        val nomesPorId = ConcurrentHashMap<Int, String?>()

        try {
            repeat(rounds) {
                val futureA = pool.submit { capturarEValidar(idA, nomesPorId, errors) }
                val futureB = pool.submit { capturarEValidar(idB, nomesPorId, errors) }
                runCatching {
                    futureA.get()
                    futureB.get()
                }.onFailure(errors::add)
            }
        } finally {
            pool.shutdown()
        }

        assertTrue(
            errors.isEmpty(),
            "Buscas concorrentes falharam ou se contaminaram: ${errors.map { it.message }}",
        )
    }

    private fun capturarEValidar(
        id: Int,
        nomesPorId: ConcurrentHashMap<Int, String?>,
        errors: CopyOnWriteArrayList<Throwable>,
    ) {
        runCatching {
            val cliente = pessoaService.getCliente(id)
            // O guard de identidade ja garante codigo == id; reforcamos aqui.
            assertEquals(id, cliente.codigo, "Codigo retornado diferente do solicitado para id $id")
            val nome = cliente.nome ?: cliente.nomeFantasia
            val anterior = nomesPorId.putIfAbsent(id, nome)
            if (anterior != null) {
                assertEquals(anterior, nome, "Nome do id $id mudou entre buscas concorrentes (contaminacao)")
            }
        }.onFailure(errors::add)
    }

    private fun envInt(name: String, default: Int): Int {
        return System.getenv(name)?.trim()?.toIntOrNull() ?: default
    }
}
