package br.andrew.dealerlenium.controller

import br.andrew.dealerlenium.model.Cliente
import br.andrew.dealerlenium.service.PessoaService
import jakarta.validation.constraints.Positive
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/debug/pessoa")
class DebugPessoaController(
    private val pessoaService: PessoaService,
) {
    @GetMapping("/{id}")
    fun getPessoa(
        @PathVariable @Positive id: Int,
    ): Cliente {
        return pessoaService.getCliente(id)
    }
}
