package br.andrew.dealerlenium.controller

import br.andrew.dealerlenium.pages.ContasReceberRegistro
import br.andrew.dealerlenium.service.TituloConsultaService
import jakarta.validation.constraints.Positive
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/titulos")
class TituloController(
    private val tituloConsultaService: TituloConsultaService,
) {
    @GetMapping("/valor")
    fun buscarValorTitulo(@RequestParam @Positive idTitulo: Int): TituloValorResponse {
        val conta = tituloConsultaService.buscarValorTituloDisponivel("all", idTitulo)
        return TituloValorResponse(
            idTitulo = idTitulo,
            conta = conta,
        )
    }
}

data class TituloValorResponse(
    val idTitulo: Int,
    val conta: ContasReceberRegistro,
)
