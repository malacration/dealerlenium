package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.AdiantamentoProperties
import br.andrew.dealerlenium.infrastructure.configurations.EmpresaConfig
import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.model.ContaReceber
import br.andrew.dealerlenium.model.SituacaoContaReceber
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.math.BigDecimal
import java.time.LocalDate

internal fun empresasReceber() = EmpresaProperties(mapOf(
    "FILIAL_A" to empresaReceber(1), "FILIAL_B" to empresaReceber(2),
))

internal fun empresaReceber(codigo: Int?) = EmpresaConfig(
    cnpj = "00000000000191", marca = "TESTE", descricao = "Filial exemplo",
    adiantamento = AdiantamentoProperties("1", "2", "3", "TESTE", "TESTE"), codigoDealer = codigo,
)

internal fun usuarioReceber(vararg roles: String) = UsernamePasswordAuthenticationToken(
    "operador", "", roles.map(::SimpleGrantedAuthority),
)

internal fun tituloWs(id: Int = 123, empresa: Int = 1, status: String = "AUT", saldo: String = "50.00") =
    ObjectMapper().readTree("""
        {"Titulo_Codigo":$id,"Titulo_EmpresaCod":$empresa,"Titulo_MovimentoFinanceiro":"R",
         "Titulo_Numero":456,"Titulo_NumeroParcela":"456-2","Titulo_PessoaCod":789,
         "Titulo_PessoaNom":"José Exemplo","Titulo_PessoaDocIdentificador":"123.456.789-00",
         "Titulo_DataEmissao":"2026-10-01T00:00:00","Titulo_DataVencimento":"2026-10-20T00:00:00",
         "Titulo_Valor":"100.00","Titulo_Saldo":"$saldo","Titulo_Status":"$status"}
    """.trimIndent())

internal fun contaReceber() = ContaReceber(
    "123", "FILIAL_A", "Filial exemplo", "456", "2", "789", "Cliente exemplo", null,
    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20),
    BigDecimal("100.00"), BigDecimal("50.00"), SituacaoContaReceber.PARCIAL, true,
)
