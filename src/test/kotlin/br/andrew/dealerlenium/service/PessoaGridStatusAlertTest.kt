package br.andrew.dealerlenium.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PessoaGridStatusAlertTest {

    @Test
    fun `returns alert title when status image is alert icon`() {
        val message = PessoaGridStatusAlert.messageOrNull(
            src = "/Resources/IcoAlerta.png",
            title = "Pendencias de Cadastro",
        )

        assertEquals("Pendencias de Cadastro", message)
    }

    @Test
    fun `does not block customer lookup when status image is white icon`() {
        val message = PessoaGridStatusAlert.messageOrNull(
            src = "/Resources/IcoBranco.png",
            title = "",
        )

        assertNull(message)
    }
}
