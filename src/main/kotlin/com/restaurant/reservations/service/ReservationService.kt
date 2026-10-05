package com.restaurant.reservations.service

import com.restaurant.reservations.dto.PublicReservationRequest
import com.restaurant.reservations.dto.ReservationRequest
import com.restaurant.reservations.dto.ReservationResponse
import com.restaurant.reservations.dto.TableMapResponse
import com.restaurant.reservations.dto.TableMapTableResponse
import com.restaurant.reservations.dto.TableMapZoneResponse
import com.restaurant.reservations.exception.ReservationConflictException
import com.restaurant.reservations.exception.ResourceNotFoundException
import com.restaurant.reservations.exception.ValidationBusinessException
import com.restaurant.reservations.model.Reservation
import com.restaurant.reservations.model.ReservationStatus
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.TableStatus
import com.restaurant.reservations.model.User
import com.restaurant.reservations.repository.ReservationRepository
import com.restaurant.reservations.repository.TableRepository
import com.restaurant.reservations.repository.UserRepository
<<<<<<< HEAD
=======
import org.slf4j.LoggerFactory
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val tableRepository: TableRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authService: AuthService
) {
<<<<<<< HEAD
=======
    private val log = LoggerFactory.getLogger(ReservationService::class.java)
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
    private val blockingStatuses = listOf(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)
    private val reservationSlotHours = 2L
    
    @Transactional
    fun createReservation(request: ReservationRequest): ReservationResponse {
        val customerId = authService.getCurrentUserId()
        
        val table = tableRepository.findByIdForUpdate(request.tableId)
            .orElseThrow { ResourceNotFoundException("Table not found") }

        val customer = userRepository.findById(customerId)
            .orElseThrow { ResourceNotFoundException("Customer not found") }

        validateTableAvailability(table, request.numberOfGuests, request.reservationDate)
        val endAt = request.reservationDate.plusHours(reservationSlotHours)

        val reservation = Reservation(
            table = table,
            customer = customer,
            reservationDate = request.reservationDate,
            reservationEnd = endAt,
            numberOfGuests = request.numberOfGuests,
            specialRequests = request.specialRequests
        )
        
        return toResponse(reservationRepository.save(reservation))
    }

    @Transactional
    fun createPublicReservation(request: PublicReservationRequest): ReservationResponse {
        val table = tableRepository.findByIdAndRestaurantIdForUpdate(request.tableId, request.restaurantId)
            .orElseThrow { ResourceNotFoundException("Table not found for restaurant") }

        validateTableAvailability(table, request.numberOfGuests, request.reservationDate)
        val endAt = request.reservationDate.plusHours(reservationSlotHours)

        val customer = userRepository.findByEmailAndActiveTrue(request.customerEmail)
            .orElseGet {
                userRepository.save(
                    User(
                        email = request.customerEmail,
                        password = passwordEncoder.encode(UUID.randomUUID().toString()),
                        name = request.customerName,
                        role = Role.CUSTOMER,
                        restaurant = table.restaurant
                    )
                )
            }

        val reservation = Reservation(
            table = table,
            customer = customer,
            reservationDate = request.reservationDate,
            reservationEnd = endAt,
            numberOfGuests = request.numberOfGuests,
            status = ReservationStatus.CONFIRMED,
            specialRequests = request.specialRequests,
            confirmed = true
        )

        return toResponse(reservationRepository.save(reservation))
    }

    @Transactional(readOnly = true)
    fun getTableMap(restaurantId: Long, date: LocalDate, time: LocalTime): TableMapResponse {
        val startAt = LocalDateTime.of(date, time)
        val endAt = startAt.plusHours(reservationSlotHours)
        val reservedTableIds = reservationRepository
            .findReservedTableIds(restaurantId, startAt, endAt, blockingStatuses)
            .toSet()

        val tables = tableRepository.findByRestaurantIdAndActiveTrue(restaurantId)
            .sortedWith(compareBy({ it.zone?.sortOrder ?: Int.MAX_VALUE }, { it.gridY }, { it.gridX }, { it.tableNumber }))

        val zones = tables
            .groupBy { it.zone }
            .map { (zone, zoneTables) ->
                TableMapZoneResponse(
                    id = zone?.id,
                    name = zone?.name ?: "Zona Central",
                    code = zone?.code ?: "CENTRAL",
                    tables = zoneTables.map { table ->
                        val operational = table.status == TableStatus.AVAILABLE
                        TableMapTableResponse(
                            id = table.id!!,
                            tableNumber = table.tableNumber,
                            capacity = table.capacity,
                            gridX = table.gridX,
                            gridY = table.gridY,
                            zoneName = zone?.name ?: "Zona Central",
                            availability = when {
                                !operational -> "OCCUPIED"
                                reservedTableIds.contains(table.id) -> "OCCUPIED"
                                else -> "AVAILABLE"
                            }
                        )
                    }
                )
            }

        return TableMapResponse(
            restaurantId = restaurantId,
            date = date,
            time = time,
            zones = zones
        )
    }
    
    fun getMyReservations(): List<ReservationResponse> {
        val customerId = authService.getCurrentUserId()
        return reservationRepository.findByCustomerId(customerId)
            .map { toResponse(it) }
    }
    
    // Solo personal del restaurante dueno de la mesa: la reserva expone nombre,
    // email implicito y peticiones especiales del cliente (datos personales).
    fun getReservationsByTable(tableId: Long): List<ReservationResponse> {
        val table = tableRepository.findById(tableId)
            .orElseThrow { ResourceNotFoundException("Table not found") }
        requireSameRestaurant(table.restaurant.id)
        return reservationRepository.findByTableId(tableId)
            .map { toResponse(it) }
    }

    fun getReservationById(id: Long): ReservationResponse {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Reservation not found") }
<<<<<<< HEAD
=======
        requireCanAccess(reservation)
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
        return toResponse(reservation)
    }


    @Transactional
    fun updateReservation(id: Long, request: ReservationRequest): ReservationResponse {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Reservation not found") }

        requireCanAccess(reservation)

        val table = tableRepository.findById(request.tableId)
<<<<<<< HEAD
            .orElseThrow { IllegalArgumentException("Table not found") }
=======
            .orElseThrow { ResourceNotFoundException("Table not found") }

        // La mesa destino debe pertenecer al mismo restaurante que la reserva original:
        // si no, se puede mover una reserva al mapa de otro tenant.
        if (table.restaurant.id != reservation.table.restaurant.id) {
            throw ValidationBusinessException("Table belongs to a different restaurant")
        }
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e

        if (request.numberOfGuests > table.capacity) {
            throw IllegalArgumentException("Number of guests exceeds table capacity")
        }

        val endAt = request.reservationDate.plusHours(reservationSlotHours)
        if (reservationRepository.existsOverlappingReservationExcluding(reservation.id!!, table.id!!, request.reservationDate, endAt, blockingStatuses)) {
            throw IllegalArgumentException("Table already reserved for this time slot")
        }
        
        val updatedReservation = reservation.copy(
            table = table,
            reservationDate = request.reservationDate,
            reservationEnd = endAt,
            numberOfGuests = request.numberOfGuests,
            specialRequests = request.specialRequests
        )
        
        return toResponse(reservationRepository.save(updatedReservation))
    }
    
    @Transactional
    fun confirmReservation(id: Long): ReservationResponse {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Reservation not found") }

        requireCanAccess(reservation)

        val updatedReservation = reservation.copy(
            status = ReservationStatus.CONFIRMED,
            confirmed = true
        )

        return toResponse(reservationRepository.save(updatedReservation))
    }

    @Transactional
    fun cancelReservation(id: Long): ReservationResponse {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Reservation not found") }

        requireCanAccess(reservation)

        val updatedReservation = reservation.copy(
            status = ReservationStatus.CANCELLED
        )

        return toResponse(reservationRepository.save(updatedReservation))
    }

    @Transactional
    fun deleteReservation(id: Long) {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Reservation not found") }

        requireCanAccess(reservation)

        log.info(
            "Reserva {} eliminada por userId={} role={}",
            id, authService.getCurrentUserId(), authService.getCurrentUserRole()
        )
        reservationRepository.delete(reservation)
    }
<<<<<<< HEAD
    
=======

    /**
     * Autorizacion a nivel de objeto (evita IDOR):
     *  - el cliente dueno de la reserva,
     *  - o personal (ADMIN/EMPLOYEE) del restaurante al que pertenece la mesa.
     *
     * Se responde 404 y no 403 para no confirmar la existencia de reservas ajenas.
     */
    private fun requireCanAccess(reservation: Reservation) {
        val userId = authService.getCurrentUserId()
        if (reservation.customer.id == userId) return

        val role = authService.getCurrentUserRole()
        val restaurantId = authService.getCurrentUserRestaurantId()
        val sameRestaurant = restaurantId != null && restaurantId == reservation.table.restaurant.id
        if ((role == Role.ADMIN || role == Role.EMPLOYEE) && sameRestaurant) return

        log.warn(
            "Acceso denegado a reserva {}: userId={} role={} restaurantId={}",
            reservation.id, userId, role, restaurantId
        )
        throw ResourceNotFoundException("Reservation not found")
    }

    private fun requireSameRestaurant(restaurantId: Long?) {
        val role = authService.getCurrentUserRole()
        val callerRestaurantId = authService.getCurrentUserRestaurantId()
        if (callerRestaurantId == null || callerRestaurantId != restaurantId ||
            (role != Role.ADMIN && role != Role.EMPLOYEE && role != Role.COOK)
        ) {
            log.warn(
                "Acceso cross-tenant denegado: userId={} role={} pidio restaurantId={}",
                authService.getCurrentUserId(), role, restaurantId
            )
            throw ResourceNotFoundException("Table not found")
        }
    }


>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
    private fun validateTableAvailability(
        table: com.restaurant.reservations.model.RestaurantTable,
        numberOfGuests: Int,
        reservationDate: LocalDateTime
    ) {
        if (numberOfGuests > table.capacity) {
            throw ValidationBusinessException("Number of guests exceeds table capacity")
        }
        if (table.status != TableStatus.AVAILABLE || !table.active) {
            throw ReservationConflictException("Table is not available")
        }
        val endAt = reservationDate.plusHours(reservationSlotHours)
        if (reservationRepository.existsOverlappingReservation(table.id!!, reservationDate, endAt, blockingStatuses)) {
            throw ReservationConflictException("Table already reserved for this time slot")
        }
    }

    private fun toResponse(reservation: Reservation): ReservationResponse {
        return ReservationResponse(
            id = reservation.id!!,
            tableId = reservation.table.id!!,
            customerId = reservation.customer.id!!,
            customerName = reservation.customer.name,
            reservationDate = reservation.reservationDate,
            numberOfGuests = reservation.numberOfGuests,
            status = reservation.status.name,
            createdAt = reservation.createdAt,
            specialRequests = reservation.specialRequests,
            confirmed = reservation.confirmed
        )
    }
}
