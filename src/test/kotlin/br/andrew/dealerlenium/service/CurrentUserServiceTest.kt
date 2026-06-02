package br.andrew.dealerlenium.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentUserServiceTest {

    private val currentUserService = CurrentUserService()

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `returns authenticated username when present`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("dealer.user", "secret")

        assertEquals("dealer.user", currentUserService.getCurrentUsernameOrNull())
    }

    @Test
    fun `returns null when there is no authenticated user`() {
        SecurityContextHolder.clearContext()

        assertNull(currentUserService.getCurrentUsernameOrNull())
    }
}
