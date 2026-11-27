package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.DealerWebServiceProperties
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Notas fiscais de entrada do contas a pagar, para importacao no Nota Agil. Somente leitura.
 *
 * O WS do Dealer nao tem nota de entrada: usa o metodo `TITULO` em modo consulta com
 * `Titulo_MovimentoFinanceiro = P` e agrupa os titulos (parcelas) da mesma nota fiscal
 * (valor = soma das parcelas):
 * - id `nf{Titulo_NotaFiscalCod}` quando o titulo tem a nota vinculada;
 * - senao `p{Titulo_PessoaCod}-n{Titulo_NotaFiscalNumero}`;
 * - titulo sem nota fiscal vira item proprio `t{Titulo_Codigo}`, marcado com problema.
 * Titulos cancelados (`CAN`), de outra empresa ou a receber sao descartados (o WS pode ignorar filtro).
 * O Dealer nao informa chave de acesso, tipo nem serie.
 *
 * Nao loga documento, numero de nota nem valores.
 */
@Service
class NotasEntradaContasPagarService(
    private val webService: DealerTituloWebService,
    private val properties: DealerWebServiceProperties,
) {
    /** Relogio monotono (nanos) do prazo do lote; trocado em teste. */
    internal var relogio: () -> Long = System::nanoTime

    private fun consultar(filtros: Map<String, Any>, empresa: Int): List<Titulo> =
        webService.consultarFiltros(filtros).mapNotNull { titulo(it, empresa) }

    fun listar(req: ListagemNotasEntradaRequest): List<NotaEntradaDealer> {
        val empresa = req.empresaCodigo?.takeIf { it > 0 }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe empresaCodigo (codigo da empresa no Dealer)")
        val ids = req.ids?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()
        if (ids != null) {
            if (ids.isEmpty() || ids.size > MAX_IDS) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe de 1 a $MAX_IDS ids")
            return buscar(empresa, ids)
        }
        val (inicio, fim) = periodo(req.dataInicial, req.dataFinal)
        val fornecedor = req.cpfCnpjFornecedor?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }?.also {
            if (it.length != 11 && it.length != 14) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Fornecedor: CNPJ ou CPF completo")
        }
        val numero = req.numeroNota?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (!it.all(Char::isDigit) || it.length > 18) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Numero da nota deve ser numerico")
        }?.trimStart('0')?.ifEmpty { "0" }

        val filtros = linkedMapOf<String, Any>(
            "Titulo_EmpresaCod" to empresa,
            "Titulo_MovimentoFinanceiro" to "P",
            // ENT = data de entrada (lancamento), equivalente ao DocDate do SAP.
            "TituloTipoData" to "ENT",
            "Data_De" to inicio.toString(),
            "Data_Ate" to fim.toString(),
        )
        fornecedor?.let { filtros["Pessoa_DocIdentificador"] = it }
        numero?.toLongOrNull()?.takeIf { it > 0 }?.let { filtros["Titulo_NotaFiscalNumero"] = it }
        return agrupar(consultar(filtros, empresa))
            .filter { fornecedor == null || it.cpfCnpjFornecedor == fornecedor }
            .filter { numero == null || it.numero?.toString() == numero }
    }

    /**
     * Uma chamada ao WS por id (ate [MAX_IDS]), em sequencia. Cada chamada tem o read-timeout do WS e o
     * lote inteiro tem [DealerWebServiceProperties.prazoLote]: estourou, 504 sem lista parcial.
     */
    private fun buscar(empresa: Int, ids: List<String>): List<NotaEntradaDealer> {
        val chaves = ids.map { id -> id to (ChaveNota.ler(id) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Id de nota invalido")) }
        val limite = relogio() + properties.prazoLote.toNanos()
        return chaves.mapNotNull { (id, chave) ->
            if (relogio() - limite >= 0)
                throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Tempo esgotado ao buscar as notas no Web Service Dealer; tente com menos notas.")
            buscar(empresa, id, chave)
        }
    }

    private fun buscar(empresa: Int, id: String, chave: ChaveNota): NotaEntradaDealer? {
        val filtros = linkedMapOf<String, Any>(
            "Titulo_EmpresaCod" to empresa,
            "Titulo_MovimentoFinanceiro" to "P",
            "TituloTipoData" to "EMI",
            "Data_De" to "1900-01-01",
            "Data_Ate" to "9999-12-31",
        )
        when (chave) {
            is ChaveNota.PorCodigo -> filtros["Titulo_NotaFiscalCod"] = chave.notaFiscalCod
            is ChaveNota.PorNumero -> { filtros["Titulo_PessoaCod"] = chave.pessoaCod; filtros["Titulo_NotaFiscalNumero"] = chave.numero }
            is ChaveNota.Titulo -> filtros["Titulo_Codigo"] = chave.tituloCodigo
        }
        return agrupar(consultar(filtros, empresa)).firstOrNull { it.id == id }
    }

    private fun periodo(dataInicial: String?, dataFinal: String?): Pair<LocalDate, LocalDate> {
        val inicio = data(dataInicial, "dataInicial")
        val fim = data(dataFinal, "dataFinal")
        if (fim < inicio) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "dataFinal anterior a dataInicial")
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > MAX_DIAS)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo maximo de $MAX_DIAS dias")
        return inicio to fim
    }

    private fun data(valor: String?, campo: String): LocalDate = try {
        LocalDate.parse(valor?.trim() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe $campo"))
    } catch (e: DateTimeParseException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$campo invalida (use AAAA-MM-DD)")
    }

    // ---------------------------------------------------------------- titulos -> notas

    internal data class Titulo(
        val codigo: Int,
        val pessoaCod: Long?,
        val pessoaDocumento: String?,
        val pessoaNome: String?,
        val notaFiscalCod: Long?,
        val notaFiscalNumero: Long?,
        val valor: BigDecimal?,
        val emissao: String?,
        val entrada: String?,
        val vencimento: String?,
    ) {
        val chave: String
            get() = when {
                (notaFiscalCod ?: 0) > 0 -> "nf$notaFiscalCod"
                (notaFiscalNumero ?: 0) > 0 && (pessoaCod ?: 0) > 0 -> "p$pessoaCod-n$notaFiscalNumero"
                else -> "t$codigo"
            }
    }

    internal fun titulo(row: JsonNode, empresaCodigo: Int): Titulo? {
        val campos = row.properties().associate { normalizar(it.key) to it.value }
        fun texto(vararg nomes: String): String? = nomes.firstNotNullOfOrNull { nome ->
            campos[normalizar(nome)]?.takeUnless { it.isNull }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
        }
        fun inteiro(vararg nomes: String): Long? = texto(*nomes)?.replace(',', '.')?.toBigDecimalOrNull()
            ?.let { runCatching { it.longValueExact() }.getOrNull() }
        fun data(vararg nomes: String): String? = texto(*nomes)?.substringBefore('T')
            ?.takeUnless { it.startsWith("0000") || it.startsWith("0001") }
            ?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }

        if (inteiro("Titulo_EmpresaCod") != empresaCodigo.toLong()) return null
        if (texto("Titulo_MovimentoFinanceiro")?.uppercase() != "P") return null
        if (texto("Titulo_Status")?.uppercase() == "CAN") return null
        val codigo = inteiro("Titulo_Codigo")?.takeIf { it > 0 && it <= Int.MAX_VALUE }?.toInt() ?: return null
        return Titulo(
            codigo = codigo,
            pessoaCod = inteiro("Titulo_PessoaCod"),
            pessoaDocumento = texto("Titulo_PessoaDocIdentificador", "Titulo_PessoaDocumento", "Pessoa_DocIdentificador")
                ?.filter(Char::isDigit)?.takeIf { it.length == 11 || it.length == 14 },
            pessoaNome = texto("Titulo_PessoaNome", "Titulo_PessoaNom", "Pessoa_Nome"),
            notaFiscalCod = inteiro("Titulo_NotaFiscalCod"),
            notaFiscalNumero = inteiro("Titulo_NotaFiscalNumero", "Titulo_NotaFiscalNro"),
            valor = texto("Titulo_Valor")?.let { if (',' in it) it.replace(".", "").replace(',', '.') else it }?.toBigDecimalOrNull(),
            emissao = data("Titulo_DataEmissao"),
            entrada = data("Titulo_DataEntrada"),
            vencimento = data("Titulo_DataVencimento"),
        )
    }

    internal fun agrupar(titulos: List<Titulo>): List<NotaEntradaDealer> =
        titulos.distinctBy { it.codigo }.groupBy { it.chave }.map { (chave, grupo) ->
            val primeiro = grupo.first()
            val valores = grupo.mapNotNull { it.valor }
            val total = if (valores.size == grupo.size) valores.fold(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP) else null
            val documentos = grupo.mapNotNull { it.pessoaDocumento }.distinct()
            NotaEntradaDealer(
                id = chave,
                numero = primeiro.notaFiscalNumero?.takeIf { it > 0 },
                dataEmissao = grupo.mapNotNull { it.emissao }.minOrNull(),
                dataLancamento = grupo.mapNotNull { it.entrada }.minOrNull(),
                nomeFornecedor = primeiro.pessoaNome,
                cpfCnpjFornecedor = documentos.singleOrNull(),
                valorTotal = total,
                parcelas = grupo.sortedBy { it.vencimento ?: "" }.map { ParcelaDealer(it.codigo, it.vencimento, it.valor) },
                problema = when {
                    chave.startsWith("t") -> "Titulo sem nota fiscal vinculada no Dealer"
                    documentos.size > 1 -> "Parcelas da nota com fornecedores diferentes no Dealer"
                    total == null -> "Parcela sem valor no Dealer"
                    else -> null
                },
            )
        }.sortedWith(compareByDescending<NotaEntradaDealer> { it.dataLancamento ?: "" }.thenBy { it.id })

    internal sealed interface ChaveNota {
        data class PorCodigo(val notaFiscalCod: Long) : ChaveNota
        data class PorNumero(val pessoaCod: Long, val numero: Long) : ChaveNota
        data class Titulo(val tituloCodigo: Int) : ChaveNota

        companion object {
            private val COD = Regex("nf([1-9][0-9]{0,17})")
            private val NUM = Regex("p([1-9][0-9]{0,17})-n([1-9][0-9]{0,17})")
            private val TIT = Regex("t([1-9][0-9]{0,9})")
            fun ler(id: String): ChaveNota? =
                COD.matchEntire(id)?.let { m -> m.groupValues[1].toLongOrNull()?.let { PorCodigo(it) } }
                    ?: NUM.matchEntire(id)?.let { m ->
                        val p = m.groupValues[1].toLongOrNull(); val n = m.groupValues[2].toLongOrNull()
                        if (p != null && n != null) PorNumero(p, n) else null
                    }
                    ?: TIT.matchEntire(id)?.let { m -> m.groupValues[1].toIntOrNull()?.let { Titulo(it) } }
        }
    }

    companion object {
        const val MAX_DIAS = 31
        const val MAX_IDS = 100
        private fun normalizar(nome: String) = nome.lowercase().filter(Char::isLetterOrDigit)
    }
}

/** Filtro por periodo de entrada (ate 31 dias) ou, quando `ids` vier preenchido, busca exata pelas notas. */
data class ListagemNotasEntradaRequest(
    val empresaCodigo: Int? = null,
    val dataInicial: String? = null,
    val dataFinal: String? = null,
    val cpfCnpjFornecedor: String? = null,
    val numeroNota: String? = null,
    val ids: List<String>? = null,
)

data class ListagemNotasEntradaResponse(val notas: List<NotaEntradaDealer>)

data class NotaEntradaDealer(
    val id: String,
    val numero: Long?,
    val dataEmissao: String?,
    val dataLancamento: String?,
    val nomeFornecedor: String?,
    val cpfCnpjFornecedor: String?,
    val valorTotal: BigDecimal?,
    val parcelas: List<ParcelaDealer>,
    /** Motivo de a nota nao ser importavel; null quando ok. */
    val problema: String?,
)

data class ParcelaDealer(val tituloCodigo: Int, val vencimento: String?, val valor: BigDecimal?)
