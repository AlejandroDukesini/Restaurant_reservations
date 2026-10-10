package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.PublicReservationRequest;
import com.restaurant.reservations.dto.ReservationRequest;
import com.restaurant.reservations.dto.ReservationResponse;
import com.restaurant.reservations.dto.TableMapResponse;
import com.restaurant.reservations.dto.TableMapTableResponse;
import com.restaurant.reservations.dto.TableMapZoneResponse;
import com.restaurant.reservations.exception.ReservationConflictException;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.Reservation;
import com.restaurant.reservations.model.ReservationStatus;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.TableStatus;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.model.Zone;
import com.restaurant.reservations.repository.ReservationRepository;
import com.restaurant.reservations.repository.TableRepository;
import com.restaurant.reservations.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final List<ReservationStatus> BLOCKING_STATUSES =
        List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
    private static final long RESERVATION_SLOT_HOURS = 2L;
    private static final String DEFAULT_ZONE_NAME = "Zona Central";
    private static final String DEFAULT_ZONE_CODE = "CENTRAL";

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public ReservationService(
        ReservationRepository reservationRepository,
        TableRepository tableRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AuthService authService
    ) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @Transactional
    public ReservationResponse createReservation(ReservationRequest request) {
        Long customerId = authService.getCurrentUserId();

        RestaurantTable table = tableRepository.findByIdForUpdate(request.tableId())
            .orElseThrow(() -> new ResourceNotFoundException("Table not found"));

        User customer = userRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        validateTableAvailability(table, request.numberOfGuests(), request.reservationDate());

        Reservation reservation = new Reservation(table, customer, request.reservationDate(), request.numberOfGuests());
        reservation.setReservationEnd(request.reservationDate().plusHours(RESERVATION_SLOT_HOURS));
        reservation.setSpecialRequests(request.specialRequests());

        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse createPublicReservation(PublicReservationRequest request) {
        RestaurantTable table = tableRepository.findByIdAndRestaurantIdForUpdate(request.tableId(), request.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Table not found for restaurant"));

        validateTableAvailability(table, request.numberOfGuests(), request.reservationDate());

        User customer = userRepository.findByEmailAndActiveTrue(request.customerEmail())
            .orElseGet(() -> userRepository.save(new User(
                request.customerEmail(),
                passwordEncoder.encode(UUID.randomUUID().toString()),
                request.customerName(),
                Role.CUSTOMER,
                table.getRestaurant()
            )));

        Reservation reservation = new Reservation(table, customer, request.reservationDate(), request.numberOfGuests());
        reservation.setReservationEnd(request.reservationDate().plusHours(RESERVATION_SLOT_HOURS));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setSpecialRequests(request.specialRequests());
        reservation.setConfirmed(true);

        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public TableMapResponse getTableMap(Long restaurantId, LocalDate date, LocalTime time) {
        LocalDateTime startAt = LocalDateTime.of(date, time);
        LocalDateTime endAt = startAt.plusHours(RESERVATION_SLOT_HOURS);
        Set<Long> reservedTableIds = new HashSet<>(
            reservationRepository.findReservedTableIds(restaurantId, startAt, endAt, BLOCKING_STATUSES)
        );

        List<RestaurantTable> tables = new ArrayList<>(tableRepository.findByRestaurantIdAndActiveTrue(restaurantId));
        tables.sort(Comparator
            .comparingInt((RestaurantTable t) -> t.getZone() != null ? t.getZone().getSortOrder() : Integer.MAX_VALUE)
            .thenComparingInt(RestaurantTable::getGridY)
            .thenComparingInt(RestaurantTable::getGridX)
            .thenComparingInt(RestaurantTable::getTableNumber));

        // LinkedHashMap: conserva el orden de aparicion y admite la clave null (mesas sin zona).
        Map<Zone, List<RestaurantTable>> byZone = new LinkedHashMap<>();
        for (RestaurantTable table : tables) {
            byZone.computeIfAbsent(table.getZone(), z -> new ArrayList<>()).add(table);
        }

        List<TableMapZoneResponse> zones = new ArrayList<>();
        byZone.forEach((zone, zoneTables) -> {
            String zoneName = zone != null ? zone.getName() : DEFAULT_ZONE_NAME;
            List<TableMapTableResponse> mapTables = zoneTables.stream()
                .map(table -> {
                    boolean operational = table.getStatus() == TableStatus.AVAILABLE;
                    String availability = !operational || reservedTableIds.contains(table.getId())
                        ? "OCCUPIED"
                        : "AVAILABLE";
                    return new TableMapTableResponse(
                        table.getId(),
                        table.getTableNumber(),
                        table.getCapacity(),
                        table.getGridX(),
                        table.getGridY(),
                        zoneName,
                        availability
                    );
                })
                .toList();
            zones.add(new TableMapZoneResponse(
                zone != null ? zone.getId() : null,
                zoneName,
                zone != null ? zone.getCode() : DEFAULT_ZONE_CODE,
                mapTables
            ));
        });

        return new TableMapResponse(restaurantId, date, time, zones);
    }

    public List<ReservationResponse> getMyReservations() {
        Long customerId = authService.getCurrentUserId();
        return reservationRepository.findByCustomerId(customerId).stream()
            .map(ReservationService::toResponse)
            .toList();
    }

    // Solo personal del restaurante dueno de la mesa: la reserva expone nombre,
    // email implicito y peticiones especiales del cliente (datos personales).
    public List<ReservationResponse> getReservationsByTable(Long tableId) {
        RestaurantTable table = tableRepository.findById(tableId)
            .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        requireSameRestaurant(table.getRestaurant().getId());
        return reservationRepository.findByTableId(tableId).stream()
            .map(ReservationService::toResponse)
            .toList();
    }

    public ReservationResponse getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        requireCanAccess(reservation);
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse updateReservation(Long id, ReservationRequest request) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));

        requireCanAccess(reservation);

        RestaurantTable table = tableRepository.findById(request.tableId())
            .orElseThrow(() -> new ResourceNotFoundException("Table not found"));

        // La mesa destino debe pertenecer al mismo restaurante que la reserva original:
        // si no, se puede mover una reserva al mapa de otro tenant.
        if (!Objects.equals(table.getRestaurant().getId(), reservation.getTable().getRestaurant().getId())) {
            throw new ValidationBusinessException("Table belongs to a different restaurant");
        }

        if (request.numberOfGuests() > table.getCapacity()) {
            throw new IllegalArgumentException("Number of guests exceeds table capacity");
        }

        LocalDateTime endAt = request.reservationDate().plusHours(RESERVATION_SLOT_HOURS);
        if (reservationRepository.existsOverlappingReservationExcluding(
            reservation.getId(), table.getId(), request.reservationDate(), endAt, BLOCKING_STATUSES)) {
            throw new IllegalArgumentException("Table already reserved for this time slot");
        }

        reservation.setTable(table);
        reservation.setReservationDate(request.reservationDate());
        reservation.setReservationEnd(endAt);
        reservation.setNumberOfGuests(request.numberOfGuests());
        reservation.setSpecialRequests(request.specialRequests());

        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse confirmReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));

        requireCanAccess(reservation);

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setConfirmed(true);

        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse cancelReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));

        requireCanAccess(reservation);

        reservation.setStatus(ReservationStatus.CANCELLED);

        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public void deleteReservation(Long id) {
        Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));

        requireCanAccess(reservation);

        log.info(
            "Reserva {} eliminada por userId={} role={}",
            id, authService.getCurrentUserId(), authService.getCurrentUserRole()
        );
        reservationRepository.delete(reservation);
    }

    /**
     * Autorizacion a nivel de objeto (evita IDOR):
     *  - el cliente dueno de la reserva,
     *  - o personal (ADMIN/EMPLOYEE) del restaurante al que pertenece la mesa.
     *
     * Se responde 404 y no 403 para no confirmar la existencia de reservas ajenas.
     */
    private void requireCanAccess(Reservation reservation) {
        Long userId = authService.getCurrentUserId();
        if (Objects.equals(reservation.getCustomer().getId(), userId)) {
            return;
        }

        Role role = authService.getCurrentUserRole();
        Long restaurantId = authService.getCurrentUserRestaurantId();
        boolean sameRestaurant = restaurantId != null
            && restaurantId.equals(reservation.getTable().getRestaurant().getId());
        if ((role == Role.ADMIN || role == Role.EMPLOYEE) && sameRestaurant) {
            return;
        }

        log.warn(
            "Acceso denegado a reserva {}: userId={} role={} restaurantId={}",
            reservation.getId(), userId, role, restaurantId
        );
        throw new ResourceNotFoundException("Reservation not found");
    }

    private void requireSameRestaurant(Long restaurantId) {
        Role role = authService.getCurrentUserRole();
        Long callerRestaurantId = authService.getCurrentUserRestaurantId();
        if (callerRestaurantId == null || !callerRestaurantId.equals(restaurantId)
            || (role != Role.ADMIN && role != Role.EMPLOYEE && role != Role.COOK)) {
            log.warn(
                "Acceso cross-tenant denegado: userId={} role={} pidio restaurantId={}",
                authService.getCurrentUserId(), role, restaurantId
            );
            throw new ResourceNotFoundException("Table not found");
        }
    }

    private void validateTableAvailability(RestaurantTable table, int numberOfGuests, LocalDateTime reservationDate) {
        if (numberOfGuests > table.getCapacity()) {
            throw new ValidationBusinessException("Number of guests exceeds table capacity");
        }
        if (table.getStatus() != TableStatus.AVAILABLE || !table.isActive()) {
            throw new ReservationConflictException("Table is not available");
        }
        LocalDateTime endAt = reservationDate.plusHours(RESERVATION_SLOT_HOURS);
        if (reservationRepository.existsOverlappingReservation(table.getId(), reservationDate, endAt, BLOCKING_STATUSES)) {
            throw new ReservationConflictException("Table already reserved for this time slot");
        }
    }

    private static ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getTable().getId(),
            reservation.getCustomer().getId(),
            reservation.getCustomer().getName(),
            reservation.getReservationDate(),
            reservation.getNumberOfGuests(),
            reservation.getStatus().name(),
            reservation.getCreatedAt(),
            reservation.getSpecialRequests(),
            reservation.isConfirmed()
        );
    }
}
