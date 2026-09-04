package br.andrew.dealerlenium.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DealerSessionRoutingTest {

    private val dedicada = DealerSessionScope.Dedicada("AUTOVEMA_PVH")
    private val geral = DealerSessionScope.Geral
    private val todas = DealerSessionScope.Todas

    @Test
    fun `reads the scope from the credential parameter`() {
        assertEquals(DealerSessionScope.Geral, DealerSessionScope.of(""))
        assertEquals(DealerSessionScope.Geral, DealerSessionScope.of("   "))
        assertEquals(DealerSessionScope.Todas, DealerSessionScope.of("all"))
        assertEquals(dedicada, DealerSessionScope.of(" autovema_pvh "))
    }

    @Test
    fun `treats null and all as an operation without branch`() {
        assertNull(DealerSessionScope.alvoDe(null))
        assertNull(DealerSessionScope.alvoDe(""))
        assertNull(DealerSessionScope.alvoDe("all"))
        assertEquals("AUTOVEMA_PVH", DealerSessionScope.alvoDe(" autovema_pvh "))
    }

    @Test
    fun `dedicated session only serves its own branch`() {
        val routing = DealerSessionRouting(listOf(dedicada, geral))

        assertTrue(routing.aceita(dedicada, "AUTOVEMA_PVH"))
        assertFalse(routing.aceita(dedicada, "AUTOVEMA_VHA"))
        assertFalse(routing.aceita(dedicada, null))
    }

    @Test
    fun `general session serves every branch except the dedicated ones`() {
        val routing = DealerSessionRouting(listOf(dedicada, geral))

        assertTrue(routing.aceita(geral, null))
        assertTrue(routing.aceita(geral, "AUTOVEMA_VHA"))
        assertFalse(routing.aceita(geral, "AUTOVEMA_PVH"))
    }

    @Test
    fun `all session serves anything`() {
        val routing = DealerSessionRouting(listOf(dedicada, todas))

        assertTrue(routing.aceita(todas, null))
        assertTrue(routing.aceita(todas, "AUTOVEMA_PVH"))
        assertTrue(routing.aceita(todas, "AUTOVEMA_VHA"))
    }

    @Test
    fun `prefers the dedicated session, then the general one, then all`() {
        val routing = DealerSessionRouting(listOf(dedicada, geral, todas))
        val livres = listOf(todas, geral, dedicada)

        assertEquals(dedicada, routing.escolher(livres, { it }, "AUTOVEMA_PVH"))
        assertEquals(geral, routing.escolher(livres, { it }, "AUTOVEMA_VHA"))
        assertEquals(geral, routing.escolher(livres, { it }, null))
        assertEquals(todas, routing.escolher(listOf(todas, dedicada), { it }, null))
    }

    @Test
    fun `waits for the dedicated session instead of falling back to the general pool`() {
        val routing = DealerSessionRouting(listOf(dedicada, geral))

        assertNull(routing.escolher(listOf(geral), { it }, "AUTOVEMA_PVH"))
        assertTrue(routing.atendida("AUTOVEMA_PVH"))
    }

    @Test
    fun `reports a branch that no credential can serve`() {
        val routing = DealerSessionRouting(listOf(dedicada))

        assertFalse(routing.atendida("AUTOVEMA_VHA"))
        assertFalse(routing.atendida(null))
        assertTrue(routing.atendida("AUTOVEMA_PVH"))
    }
}
