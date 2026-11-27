package br.andrew.dealerlenium.controller

import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.service.ListagemNotasEntradaRequest
import br.andrew.dealerlenium.service.ListagemNotasEntradaResponse
import br.andrew.dealerlenium.service.NotasEntradaContasPagarService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Contas a pagar do Dealer (somente leitura) para integracoes. Exige a role
 * `integracao_contas_pagar` (service account do Nota Agil; ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/contas-pagar")
class ContasPagarController(
    private val notasEntrada: NotasEntradaContasPagarService,
    private val empresaProperties: EmpresaProperties,
) {
    /** Empresas configuradas com codigo no Dealer (Titulo_EmpresaCod), para o Nota Agil cadastrar filiais. */
    @GetMapping("/empresas")
    fun empresas(): List<EmpresaDealer> = empresaProperties.listEmpresas()
        .mapNotNull { e ->
            val codigo = e.codigoDealer?.takeIf { it > 0 } ?: return@mapNotNull null
            val cnpj = e.cnpj.filter(Char::isDigit).takeIf { it.length == 14 } ?: return@mapNotNull null
            EmpresaDealer(codigo, cnpj, e.descricao?.trim()?.takeIf { it.isNotEmpty() } ?: e.id.replace('_', ' ').trim(), e.id)
        }
        .sortedBy { it.codigoDealer }

    /** POST para nao expor CNPJ de fornecedor em URL/log de acesso. */
    @PostMapping("/notas-entrada/listagem")
    fun listarNotasEntrada(@RequestBody req: ListagemNotasEntradaRequest): ListagemNotasEntradaResponse =
        ListagemNotasEntradaResponse(notasEntrada.listar(req))
}

/** [id] e a chave em empresa.configuracoes, a mesma role que libera a empresa ao usuario (/usuario/empresas). */
data class EmpresaDealer(val codigoDealer: Int, val cnpj: String, val nome: String, val id: String)
