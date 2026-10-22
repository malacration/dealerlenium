package br.andrew.dealerlenium.controller

import br.andrew.dealerlenium.model.ContasReceberFiltro
import br.andrew.dealerlenium.model.ContasReceberPagina
import br.andrew.dealerlenium.model.PixPagamentoResponse
import br.andrew.dealerlenium.model.SituacaoContaReceber
import br.andrew.dealerlenium.model.ClientesSelecaoPagina
import br.andrew.dealerlenium.service.ContasReceberService
import br.andrew.dealerlenium.service.ContaReceberPixService
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Validated
@RestController
@RequestMapping("/api/contas-receber")
class ContasReceberController(
    private val service: ContasReceberService,
    private val pixService: ContaReceberPixService,
) {
    @GetMapping
    fun listar(
        authentication: Authentication?,
        @RequestParam(defaultValue = "0") pagina: Int,
        @RequestParam(defaultValue = "20") tamanho: Int,
        @RequestParam(defaultValue = "") busca: String,
        @RequestParam(required = false) empresaId: String?,
        @RequestParam(required = false) situacao: SituacaoContaReceber?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) vencimentoDe: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) vencimentoAte: LocalDate?,
        @RequestParam(required = false) clienteId: String?,
        @RequestParam(required = false) lancamento: Int?,
        @RequestParam(required = false) documento: Int?,
        @RequestParam(required = false) parcela: Int?,
        @RequestParam(defaultValue = "false") todos: Boolean,
    ): ContasReceberPagina = service.listar(
        ContasReceberFiltro(pagina, tamanho, busca, empresaId, situacao, vencimentoDe, vencimentoAte, clienteId, lancamento, documento, parcela), authentication, todos,
    )

    @GetMapping("/clientes")
    fun listarClientes(
        authentication: Authentication?,
        @RequestParam(required = false) empresaId: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) vencimentoDe: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) vencimentoAte: LocalDate?,
        @RequestParam(defaultValue = "") busca: String,
        @RequestParam(defaultValue = "0") pagina: Int,
        @RequestParam(defaultValue = "20") tamanho: Int,
        @RequestParam(defaultValue = "false") todos: Boolean,
    ): ClientesSelecaoPagina = service.listarClientes(
        ContasReceberFiltro(pagina, tamanho, busca, empresaId, vencimentoDe = vencimentoDe, vencimentoAte = vencimentoAte), authentication, todos,
    )

    @PostMapping("/{tituloId}/pix")
    fun gerarPix(
        @PathVariable @Positive tituloId: Int,
        @Valid @RequestBody request: ContaReceberPixRequest,
        authentication: Authentication?,
    ): PixPagamentoResponse = pixService.gerar(tituloId, request.empresaId, authentication)
}

data class ContaReceberPixRequest @JsonCreator constructor(
    @param:JsonProperty(value = "empresaId", required = true) @field:NotBlank val empresaId: String,
)
