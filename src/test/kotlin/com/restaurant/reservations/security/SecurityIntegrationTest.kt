package com.restaurant.reservations.security

import com.restaurant.reservations.model.Role
import com.restaurant.reservations.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Control de acceso por rol (RBAC), autenticacion JWT, cabeceras de seguridad y CORS
 * tal como los aplica la cadena de filtros real de Spring Security.
 */
class SecurityIntegrationTest : IntegrationTest() {

    @Test
    fun `endpoint protegido sin token responde 401`() {
        mockMvc.perform(get("/api/staff/tables"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `token invalido o manipulado responde 401`() {
        mockMvc.perform(get("/api/staff/tables").header(HttpHeaders.AUTHORIZATION, "Bearer no.es.valido"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `token valido da acceso a su propio ambito`() {
        val restaurant = data.restaurant()
        data.table(restaurant)
        val waiter = data.user(Role.EMPLOYEE, restaurant)

        mockMvc.perform(get("/api/staff/tables").authAs(waiter))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `dar de baja a un usuario revoca su token aunque no haya expirado`() {
        val restaurant = data.restaurant()
        val waiter = data.user(Role.EMPLOYEE, restaurant)
        val admin = data.user(Role.ADMIN, restaurant)
        val token = tokenFor(waiter)

        mockMvc.perform(get("/api/staff/tables").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/staff/${waiter.id}").authAs(admin)
        ).andExpect(status().isNoContent)

        // Regresion QA-SEC-02: el mismo token, aun vigente, ya no autentica.
        mockMvc.perform(get("/api/staff/tables").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isUnauthorized)
    }

    /** Matriz rol -> ruta que debe quedar denegada (403). */
    @ParameterizedTest(name = "{0} no puede acceder a {1}")
    @CsvSource(
        "COOK,     /api/employee/orders",
        "EMPLOYEE, /api/admin/staff",
        "EMPLOYEE, /api/admin/orders",
        "EMPLOYEE, /api/cook/queue",
        "COOK,     /api/admin/menu/1",
        "CUSTOMER, /api/staff/menu",
        "CUSTOMER, /api/staff/tables",
        "CUSTOMER, /api/admin/restaurants",
        "COOK,     /api/customer/reservations"
    )
    fun `roles sin permiso reciben 403`(role: Role, path: String) {
        val restaurant = data.restaurant()
        val user = data.user(role, if (role == Role.CUSTOMER) null else restaurant)

        mockMvc.perform(get(path).authAs(user))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `reservas de una mesa ya no son publicas`() {
        val table = data.table(data.restaurant())

        // Sin token, @PreAuthorize lanza AccessDenied y el handler global responde 403.
        mockMvc.perform(get("/api/public/tables/${table.id}/reservations"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `endpoints publicos responden sin autenticacion`() {
        val restaurant = data.restaurant("Casa Publica")

        mockMvc.perform(get("/api/public/restaurants"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].slug").value("casa-publica"))
        mockMvc.perform(get("/api/public/restaurants/casa-publica/tables"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/public/restaurants/${restaurant.id}/table-map?date=2030-01-01&time=20:00"))
            .andExpect(status().isOk)
    }

    @Test
    fun `respuestas incluyen las cabeceras de seguridad del navegador`() {
        mockMvc.perform(get("/api/public/restaurants"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
            .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
            .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
            .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("object-src 'none'")))
            .andExpect(header().exists("Permissions-Policy"))
    }

    @Test
    fun `CORS permite el servidor de desarrollo local`() {
        mockMvc.perform(
            options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        ).andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
    }

    @Test
    fun `CORS rechaza origenes no permitidos`() {
        mockMvc.perform(
            options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "https://atacante.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        ).andExpect(status().isForbidden)
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @Test
    fun `ruta API inexistente no devuelve 500`() {
        val admin = data.user(Role.ADMIN, data.restaurant())
        mockMvc.perform(get("/api/no-existe").authAs(admin))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `metodo HTTP no soportado responde 405 y no 500`() {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/public/restaurants"))
            .andExpect(status().isMethodNotAllowed)
    }
}
