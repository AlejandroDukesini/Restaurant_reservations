package com.restaurant.reservations.mobile.domain

import com.restaurant.reservations.mobile.data.MenuItemDto
import com.restaurant.reservations.mobile.data.OrderItemRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationsTest {

    @Test
    fun cadaRolVeLasMismasSeccionesQueEnLaWeb() {
        assertEquals(listOf(Destination.FLOOR, Destination.KITCHEN, Destination.RESERVATIONS), destinationsFor("ADMIN"))
        assertEquals(listOf(Destination.FLOOR), destinationsFor("EMPLOYEE"))
        assertEquals(listOf(Destination.KITCHEN), destinationsFor("COOK"))
    }

    @Test
    fun clientesYRolesDesconocidosNoTienenSecciones() {
        assertTrue(destinationsFor("CUSTOMER").isEmpty())
        assertTrue(destinationsFor("INVENTADO").isEmpty())
        assertTrue(destinationsFor(null).isEmpty())
    }
}

class OrderDraftTest {

    private val menu = listOf(
        MenuItemDto(id = 100, name = "Risotto", category = "MAIN", price = 32.0),
        MenuItemDto(id = 200, name = "Vino", category = "DRINK", price = 12.5)
    )

    @Test
    fun sumaCantidadesYCalculaElTotal() {
        val draft = OrderDraft().change(100, 1).change(100, 1).change(200, 1)
        assertEquals(2, draft.quantityOf(100))
        assertEquals(76.5, draft.total(menu), 0.0001) // 32*2 + 12.5, como en la prueba del SPA
    }

    @Test
    fun llegarACeroQuitaElPlatoYNoBajaDeCero() {
        val draft = OrderDraft().change(200, 1).change(200, -1).change(200, -1)
        assertEquals(0, draft.quantityOf(200))
        assertTrue(draft.isEmpty())
    }

    @Test
    fun soloEnviaIdYCantidad() {
        val items = OrderDraft().change(100, 2).toRequestItems()
        assertEquals(listOf(OrderItemRequest(menuItemId = 100, quantity = 2)), items)
    }

    @Test
    fun platoQueYaNoEstaEnElMenuNoSumaAlTotal() {
        assertEquals(0.0, OrderDraft().change(999, 3).total(menu), 0.0)
    }
}

class LabelsTest {

    @Test
    fun formateaImportesComoLaWeb() {
        assertEquals("$76.50", money(76.5))
        assertEquals("$0.00", money(0.0))
    }

    @Test
    fun formateaFechasIsoYDevuelveLasInvalidasTalCual() {
        assertEquals("01/05/2030 20:00", formatDateTime("2030-05-01T20:00:00"))
        assertEquals("no-es-fecha", formatDateTime("no-es-fecha"))
    }

    @Test
    fun accionesSegunElEstado() {
        assertTrue(canConfirmReservation("PENDING"))
        assertFalse(canConfirmReservation("CONFIRMED"))
        assertTrue(canCancelReservation("CONFIRMED"))
        assertFalse(canCancelReservation("CANCELLED"))
        assertTrue(canMarkPreparing("PENDING"))
        assertFalse(canMarkPreparing("PREPARING"))
    }

    @Test
    fun lasEtiquetasCubrenTodosLosEstadosDelBackend() {
        assertEquals(setOf("PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"), ORDER_STATUS_LABEL.keys)
        assertEquals(setOf("PENDING", "PREPARING", "READY"), ITEM_STATUS_LABEL.keys)
        assertEquals(setOf("PENDING", "CONFIRMED", "CANCELLED", "COMPLETED"), RESERVATION_STATUS_LABEL.keys)
        assertEquals(setOf("STARTER", "MAIN", "DESSERT", "DRINK"), CATEGORY_LABEL.keys)
    }
}
