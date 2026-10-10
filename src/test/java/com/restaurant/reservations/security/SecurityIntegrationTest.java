package com.restaurant.reservations.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;

/**
 * Control de acceso por rol (RBAC), autenticacion JWT, cabeceras de seguridad y CORS
 * tal como los aplica la cadena de filtros real de Spring Security.
 */
class SecurityIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("endpoint protegido sin token responde 401")
    void endpointProtegidoSinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/staff/tables"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token invalido o manipulado responde 401")
    void tokenInvalidoResponde401() throws Exception {
        mockMvc.perform(get("/api/staff/tables").header(HttpHeaders.AUTHORIZATION, "Bearer no.es.valido"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token valido da acceso a su propio ambito")
    void tokenValidoDaAccesoASuPropioAmbito() throws Exception {
        Restaurant restaurant = data.restaurant();
        data.table(restaurant);
        User waiter = data.user(Role.EMPLOYEE, restaurant);

        mockMvc.perform(get("/api/staff/tables").with(authAs(waiter)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("dar de baja a un usuario revoca su token aunque no haya expirado")
    void darDeBajaRevocaElToken() throws Exception {
        Restaurant restaurant = data.restaurant();
        User waiter = data.user(Role.EMPLOYEE, restaurant);
        User admin = data.user(Role.ADMIN, restaurant);
        String token = tokenFor(waiter);

        mockMvc.perform(get("/api/staff/tables").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/staff/" + waiter.getId()).with(authAs(admin)))
            .andExpect(status().isNoContent());

        // Regresion QA-SEC-02: el mismo token, aun vigente, ya no autentica.
        mockMvc.perform(get("/api/staff/tables").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    /** Matriz rol -> ruta que debe quedar denegada (403). */
    @ParameterizedTest(name = "{0} no puede acceder a {1}")
    @CsvSource({
        "COOK,     /api/employee/orders",
        "EMPLOYEE, /api/admin/staff",
        "EMPLOYEE, /api/admin/orders",
        "EMPLOYEE, /api/cook/queue",
        "COOK,     /api/admin/menu/1",
        "CUSTOMER, /api/staff/menu",
        "CUSTOMER, /api/staff/tables",
        "CUSTOMER, /api/admin/restaurants",
        "COOK,     /api/customer/reservations"
    })
    @DisplayName("roles sin permiso reciben 403")
    void rolesSinPermisoReciben403(Role role, String path) throws Exception {
        Restaurant restaurant = data.restaurant();
        User user = data.user(role, role == Role.CUSTOMER ? null : restaurant);

        mockMvc.perform(get(path).with(authAs(user)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("reservas de una mesa ya no son publicas")
    void reservasDeUnaMesaYaNoSonPublicas() throws Exception {
        RestaurantTable table = data.table(data.restaurant());

        // Sin token, @PreAuthorize lanza AccessDenied y el handler global responde 403.
        mockMvc.perform(get("/api/public/tables/" + table.getId() + "/reservations"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("endpoints publicos responden sin autenticacion")
    void endpointsPublicosRespondenSinAutenticacion() throws Exception {
        Restaurant restaurant = data.restaurant("Casa Publica");

        mockMvc.perform(get("/api/public/restaurants"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value("casa-publica"));
        mockMvc.perform(get("/api/public/restaurants/casa-publica/tables"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/restaurants/" + restaurant.getId() + "/table-map?date=2030-01-01&time=20:00"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("respuestas incluyen las cabeceras de seguridad del navegador")
    void respuestasIncluyenCabecerasDeSeguridad() throws Exception {
        mockMvc.perform(get("/api/public/restaurants"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
            .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
            .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
            .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
            .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    @DisplayName("CORS permite el servidor de desarrollo local")
    void corsPermiteElServidorDeDesarrolloLocal() throws Exception {
        mockMvc.perform(
                options("/api/auth/login")
                    .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    @DisplayName("CORS rechaza origenes no permitidos")
    void corsRechazaOrigenesNoPermitidos() throws Exception {
        mockMvc.perform(
                options("/api/auth/login")
                    .header(HttpHeaders.ORIGIN, "https://atacante.example")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            )
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("ruta API inexistente no devuelve 500")
    void rutaApiInexistenteNoDevuelve500() throws Exception {
        User admin = data.user(Role.ADMIN, data.restaurant());
        mockMvc.perform(get("/api/no-existe").with(authAs(admin)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("metodo HTTP no soportado responde 405 y no 500")
    void metodoNoSoportadoResponde405() throws Exception {
        mockMvc.perform(delete("/api/public/restaurants"))
            .andExpect(status().isMethodNotAllowed());
    }
}
