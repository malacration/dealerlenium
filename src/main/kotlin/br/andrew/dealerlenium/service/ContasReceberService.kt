package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.Empresa
import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.model.ContaReceber
import br.andrew.dealerlenium.model.ContasReceberFiltro
import br.andrew.dealerlenium.model.ContasReceberPagina
import br.andrew.dealerlenium.model.SituacaoContaReceber
import br.andrew.dealerlenium.model.ClientesSelecaoPagina
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.text.Normalizer

@Service
class ContasReceberService(
    private val empresas: EmpresaProperties,
    private val acesso: UsuarioEmpresaService,
    private val webService: DealerTituloWebService,
    private val mapper: DealerTituloMapper,
) {
    fun listar(filtro: ContasReceberFiltro, authentication: Authentication?, todos: Boolean = false): ContasReceberPagina {
        val busca = normalize(filtro.busca.trim())
        val documento = documentoBusca(busca)
        val titulos = carregarTitulos(filtro, authentication).filter { titulo ->
            (when (filtro.situacao) {
                null -> true
                SituacaoContaReceber.ABERTO ->
                    titulo.saldoAberto.signum() > 0 && titulo.situacao in setOf(
                        SituacaoContaReceber.ABERTO,
                        SituacaoContaReceber.PARCIAL,
                    )
                else -> titulo.situacao == filtro.situacao
            }) && (documento != null || matches(titulo, busca))
        }.sortedWith(compareBy(ContaReceber::vencimento, ContaReceber::empresaId, ContaReceber::tituloId))
        val offset = filtro.pagina.toLong() * filtro.tamanho
        val itens = if (todos) titulos else if (offset >= titulos.size) emptyList() else titulos.drop(offset.toInt()).take(filtro.tamanho)
        return ContasReceberPagina(itens, titulos.size)
    }

    fun listarClientes(filtro: ContasReceberFiltro, authentication: Authentication?, todos: Boolean = false): ClientesSelecaoPagina {
        // CONSULTAPESSOA is global: validate branch access, but never query titles or send dates.
        filtro.copy(vencimentoDe = null, vencimentoAte = null, clienteId = null).validar(consultaClientes = true)
        val permitidas = empresasPermitidas(authentication)
        val empresa = filtro.empresaId?.trim()?.takeIf { it.isNotEmpty() }?.let { resolveEmpresa(it, permitidas) }
            ?: permitidas.firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Nenhuma filial permitida para este usuario.")
        codigo(empresa)
        val documento = documentoBusca(filtro.busca.trim())
            ?: throw IllegalArgumentException("Informe o CPF ou CNPJ completo do cliente. O Web Service de pessoas nao permite busca por nome ou codigo.")
        val clientes = webService.consultarPessoa(documento).sortedWith(compareBy({ normalize(it.nome) }, { it.id }))
        val offset = filtro.pagina.toLong() * filtro.tamanho
        return ClientesSelecaoPagina(
            if (todos) clientes else if (offset >= clientes.size) emptyList() else clientes.drop(offset.toInt()).take(filtro.tamanho), clientes.size,
        )
    }

    private fun documentoBusca(busca: String): String? = busca.filter(Char::isDigit).takeIf {
        it.length in setOf(11, 14) && busca.all { char -> char.isDigit() || char in "./- " }
    }

    private fun carregarTitulos(filtro: ContasReceberFiltro, authentication: Authentication?): List<ContaReceber> {
        filtro.validar()
        val permitidas = empresasPermitidas(authentication)
        val selecionadas = filtro.empresaId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            listOf(resolveEmpresa(it, permitidas))
        } ?: permitidas
        val busca = normalize(filtro.busca.trim())
        val documento = documentoBusca(busca)
        return selecionadas.flatMap { empresa ->
            val codigo = codigo(empresa)
            webService.consultar(codigo, filtro.vencimentoDe, filtro.vencimentoAte,
                pessoaDocumento = documento, pessoaCodigo = filtro.clienteId?.takeIf { it.isNotBlank() }?.toInt(),
                tituloCodigo = filtro.lancamento, tituloNumero = filtro.documento, tituloParcela = filtro.parcela)
                .mapNotNull { mapper.map(it, empresa) }
        }.filter { titulo ->
            (filtro.lancamento == null || titulo.tituloId.toIntOrNull() == filtro.lancamento) &&
                (filtro.documento == null || titulo.documento.toIntOrNull() == filtro.documento) &&
                (filtro.parcela == null || titulo.parcela?.toIntOrNull() == filtro.parcela) &&
                (filtro.clienteId.isNullOrBlank() || titulo.clienteId == filtro.clienteId) &&
                (filtro.vencimentoDe == null || !titulo.vencimento.isBefore(filtro.vencimentoDe)) &&
                (filtro.vencimentoAte == null || !titulo.vencimento.isAfter(filtro.vencimentoAte))
        }.distinctBy { it.empresaId to it.tituloId }
    }

    fun empresaAutorizada(empresaId: String, authentication: Authentication?): Empresa =
        resolveEmpresa(empresaId.trim(), empresasPermitidas(authentication))

    fun consultarTitulo(empresa: Empresa, tituloId: Int): ContaReceber {
        return webService.consultar(codigo(empresa), tituloCodigo = tituloId)
            .mapNotNull { mapper.map(it, empresa) }
            .singleOrNull { it.tituloId == tituloId.toString() }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Titulo nao encontrado nesta filial.")
    }

    private fun empresasPermitidas(authentication: Authentication?): List<Empresa> {
        if (authentication == null || !authentication.isAuthenticated) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticacao necessaria para consultar titulos.")
        }
        return empresas.listEmpresas(acesso.getEmpresaIds(authentication))
    }

    private fun resolveEmpresa(id: String, permitidas: List<Empresa>): Empresa {
        val empresa = permitidas.firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: permitidas.singleOrNull { it.codigoDealer?.toString() == id }
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Filial nao permitida para este usuario.")
        codigo(empresa)
        return empresa
    }

    private fun codigo(empresa: Empresa): Int {
        val codigo = empresa.codigoDealer?.takeIf { it > 0 }
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure codigo-dealer para a filial ${empresa.id}.")
        if (empresas.listEmpresas().count { it.codigoDealer == codigo } != 1) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Codigo Dealer duplicado na configuracao de filiais.")
        }
        return codigo
    }

    private fun matches(titulo: ContaReceber, busca: String): Boolean {
        if (busca.isEmpty()) return true
        if (listOf(titulo.tituloId, titulo.documento, titulo.clienteId, titulo.clienteNome, titulo.clienteDocumento.orEmpty())
                .any { normalize(it).contains(busca) }) return true
        val digits = busca.filter(Char::isDigit)
        return digits.isNotEmpty() && busca.all { it.isDigit() || it in "./- " } &&
            titulo.clienteDocumento?.filter(Char::isDigit)?.contains(digits) == true
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase()
}
