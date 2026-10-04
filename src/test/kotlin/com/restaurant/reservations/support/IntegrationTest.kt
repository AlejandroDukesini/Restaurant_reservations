package com.restaurant.reservations.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.restaurant.reservations.model.User
import com.restaurant.reservations.repository.MenuItemRepository
import com.restaurant.reservations.repository.OrderItemRepository
import com.restaurant.reservations.repository.OrderRepository
import com.restaurant.reservations.repository.ReservationRepository
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.TableRepository
import com.restaurant.reservations.repository.UserRepository
import com.restaurant.reservations.repository.ZoneRepository
import com.restaurant.reservations.security.JwtTokenProvider
import com.restaurant.reservations.security.UserPrincipal
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

/**
 * Base de las pruebas de integracion: contexto completo de Spring (seguridad,
 * filtros, controladores, servicios y JPA reales) sobre H2 en modo PostgreSQL.
 *
 * No se usa @Transactional en las pruebas a proposito: asi cada peticion MockMvc
 * abre y confirma su propia transaccion como en produccion, y se detectan
 * problemas de carga lazy o de bloqueo que un rollback ocultaria. El aislamiento
 * se consigue vaciando las tablas despues de cada prueba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTest {

    @Autowired protected lateinit var mockMvc: MockMvc
    @Autowired protected lateinit var objectMapper: ObjectMapper
    @Autowired protected lateinit var data: TestData
    @Autowired protected lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var orderItemRepository: OrderItemRepository
    @Autowired private lateinit var orderRepository: OrderRepository
    @Autowired private lateinit var reservationRepository: ReservationRepository
    @Autowired private lateinit var menuItemRepository: MenuItemRepository
    @Autowired private lateinit var tableRepository: TableRepository
    @Autowired private lateinit var zoneRepository: ZoneRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var restaurantRepository: RestaurantRepository

    @AfterEach
    fun cleanDatabase() {
        // Orden inverso a las claves foraneas.
        orderItemRepository.deleteAllInBatch()
        orderRepository.deleteAllInBatch()
        reservationRepository.deleteAllInBatch()
        menuItemRepository.deleteAllInBatch()
        tableRepository.deleteAllInBatch()
        zoneRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        restaurantRepository.deleteAllInBatch()
    }

    /** JWT real firmado por la aplicacion para un usuario ya persistido. */
    protected fun tokenFor(user: User): String {
        val principal = UserPrincipal(
            id = user.id!!,
            email = user.email,
            privatePassword = user.password,
            role = user.role,
            restaurantId = user.restaurant?.id
        )
        return tokenProvider.generateToken(
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        )
    }

    protected fun MockHttpServletRequestBuilder.authAs(user: User): MockHttpServletRequestBuilder =
        header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenFor(user)}")

    protected fun MockHttpServletRequestBuilder.json(body: Any): MockHttpServletRequestBuilder =
        contentType(MediaType.APPLICATION_JSON).content(
            if (body is String) body else objectMapper.writeValueAsString(body)
        )

    protected fun MvcResult.body(): JsonNode =
        objectMapper.readTree(response.contentAsString)
}
