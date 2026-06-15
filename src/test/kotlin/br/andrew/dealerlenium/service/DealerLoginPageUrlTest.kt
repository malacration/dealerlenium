package br.andrew.dealerlenium.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DealerLoginPageUrlTest {

    @Test
    fun `recognizes production LoginAux redirect with Windows query`() {
        assertTrue(
            isDealerLoginPageUrl(
                "https://gruporovema.dealernetworkflow.com.br/LoginAux.aspx?Windows",
            ),
        )
    }

    @Test
    fun `recognizes standard login page ignoring case and query`() {
        assertTrue(
            isDealerLoginPageUrl(
                "https://gruporovema.dealernetworkflow.com.br/LOGIN.ASPX?redirect=portal",
            ),
        )
    }

    @Test
    fun `recognizes login occurrence with arbitrary content after page name`() {
        assertTrue(
            isDealerLoginPageUrl(
                "https://gruporovema.dealernetworkflow.com.br/login.aspx/qualquer-coisa?x=1#teste",
            ),
        )
    }

    @Test
    fun `recognizes configured LoginAux page without query`() {
        assertTrue(
            isDealerLoginPageUrl(
                "https://hmlgruporovema.dealernetworkflow.com.br/LoginAux.aspx",
            ),
        )
    }

    @Test
    fun `does not classify portal page as login`() {
        assertFalse(
            isDealerLoginPageUrl(
                "https://gruporovema.dealernetworkflow.com.br/Portal/default.html",
            ),
        )
    }

    @Test
    fun `does not classify unrelated authentication page as login`() {
        assertFalse(
            isDealerLoginPageUrl(
                "https://gruporovema.dealernetworkflow.com.br/Authentication.aspx",
            ),
        )
    }
}
