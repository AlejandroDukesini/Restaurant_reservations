package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.TableRequest;
import com.restaurant.reservations.dto.TableResponse;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.StateConflictException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.Permission;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.StaffGroup;
import com.restaurant.reservations.model.TableSessionStatus;
import com.restaurant.reservations.model.TableStatus;
import com.restaurant.reservations.model.Zone;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.repository.ReservationRepository;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.StaffGroupRepository;
import com.restaurant.reservations.repository.TableRepository;
import com.restaurant.reservations.repository.TableSessionRepository;
import com.restaurant.reservations.repository.ZoneRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administracion de mesas (CRUD). Antes cualquier mesero podia crear, editar y borrar mesas;
 * ahora requiere ser gerente o tener MANAGE_TABLES. La lectura sigue abierta al personal.
 */
@Service
public class TableService {

    private static final Logger log = LoggerFactory.getLogger(TableService.class);

    private final TableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final ZoneRepository zoneRepository;
    private final StaffGroupRepository groupRepository;
    private final TableSessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final AccessService access;
    private final RealtimeService realtime;

    public TableService(
        TableRepository tableRepository,
        RestaurantRepository restaurantRepository,
        ZoneRepository zoneRepository,
        StaffGroupRepository groupRepository,
        TableSessionRepository sessionRepository,
        OrderRepository orderRepository,
        ReservationRepository reservationRepository,
        AccessService access,
        RealtimeService realtime
    ) {
        this.tableRepository = tableRepository;
        this.restaurantRepository = restaurantRepository;
        this.zoneRepository = zoneRepository;
        this.groupRepository = groupRepository;
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.access = access;
        this.realtime = realtime;
    }

    @Transactional
    public TableResponse createTable(TableRequest request) {
        access.require(Permission.MANAGE_TABLES);
        Long restaurantId = access.restaurantId();
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        requireUniqueNumber(restaurantId, request.tableNumber(), null);
        RestaurantTable table = new RestaurantTable(request.tableNumber(), request.floor(), request.capacity(),
            request.price(), restaurant);
        apply(table, request, restaurantId);
        TableResponse response = toResponse(tableRepository.save(table));
        changed();
        return response;
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getTablesByRestaurant(Long restaurantId) {
        return tableRepository.findByRestaurantIdAndActiveTrue(restaurantId).stream()
            .sorted(ORDER).map(TableService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getMyTables(Integer floor) {
        Long restaurantId = access.restaurantId();
        return floor != null ? getTablesByFloor(restaurantId, floor) : getTablesByRestaurant(restaurantId);
    }

    /** Administracion: todas las mesas, tambien las desactivadas. */
    @Transactional(readOnly = true)
    public List<TableResponse> getAllTables() {
        access.require(Permission.MANAGE_TABLES);
        return tableRepository.findByRestaurantId(access.restaurantId()).stream()
            .sorted(ORDER).map(TableService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getTablesByFloor(Long restaurantId, int floor) {
        return tableRepository.findByRestaurantIdAndFloorAndActiveTrue(restaurantId, floor).stream()
            .sorted(ORDER).map(TableService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TableResponse getTableById(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public TableResponse updateTable(Long id, TableRequest request) {
        access.require(Permission.MANAGE_TABLES);
        RestaurantTable table = find(id);
        requireUniqueNumber(table.getRestaurant().getId(), request.tableNumber(), table.getId());
        table.setTableNumber(request.tableNumber());
        table.setFloor(request.floor());
        table.setCapacity(request.capacity());
        table.setPrice(request.price());
        apply(table, request, table.getRestaurant().getId());
        TableResponse response = toResponse(tableRepository.save(table));
        changed();
        return response;
    }

    /** Activar / desactivar. No se desactiva una mesa con una atencion en curso. */
    @Transactional
    public TableResponse setActive(Long id, boolean active) {
        access.require(Permission.MANAGE_TABLES);
        RestaurantTable table = find(id);
        if (active) {
            requireUniqueNumber(table.getRestaurant().getId(), table.getTableNumber(), table.getId());
        } else {
            requireNoOpenSession(table);
        }
        table.setActive(active);
        TableResponse response = toResponse(tableRepository.save(table));
        changed();
        return response;
    }

    /**
     * Eliminar: si la mesa tiene historial (atenciones, pedidos o reservas) solo se desactiva,
     * para no perder metricas; si no tiene nada, se borra definitivamente.
     */
    @Transactional
    public void deleteTable(Long id) {
        access.require(Permission.MANAGE_TABLES);
        RestaurantTable table = find(id);
        requireNoOpenSession(table);
        boolean history = sessionRepository.existsByTableId(table.getId())
            || orderRepository.existsByTableId(table.getId())
            || reservationRepository.existsByTableId(table.getId());
        if (history) {
            table.setActive(false);
            tableRepository.save(table);
        } else {
            tableRepository.delete(table);
        }
        log.info("Mesa {} {} por userId={}", id, history ? "desactivada" : "eliminada", access.userId());
        changed();
    }

    private void apply(RestaurantTable table, TableRequest request, Long restaurantId) {
        Zone zone = null;
        if (request.zoneId() != null) {
            zone = zoneRepository.findByIdAndRestaurantId(request.zoneId(), restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found for restaurant"));
        }
        StaffGroup group = null;
        if (request.groupId() != null) {
            group = groupRepository.findByIdAndRestaurantIdAndActiveTrue(request.groupId(), restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        }
        table.setName(request.name() == null || request.name().isBlank() ? null : request.name().strip());
        table.setZone(zone);
        table.setGroup(group);
        table.setGridX(request.gridX());
        table.setGridY(request.gridY());
        table.setSortOrder(request.sortOrder());
        if (request.status() != null) {
            TableStatus status;
            try {
                status = TableStatus.valueOf(request.status().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ValidationBusinessException("Invalid table status");
            }
            if (status == TableStatus.OCCUPIED) {
                throw new ValidationBusinessException("Occupation is managed by opening the table");
            }
            if (status == TableStatus.MAINTENANCE) {
                requireNoOpenSession(table);
            }
            table.setStatus(status);
        }
    }

    private void requireNoOpenSession(RestaurantTable table) {
        if (table.getId() != null
            && sessionRepository.findOpenByTable(table.getId(), TableSessionStatus.CLOSED).isPresent()) {
            throw new StateConflictException("The table has an open session; close it first");
        }
    }

    private void requireUniqueNumber(Long restaurantId, Integer number, Long excludeId) {
        boolean taken = tableRepository.findByRestaurantIdAndActiveTrue(restaurantId).stream()
            .anyMatch(t -> t.getTableNumber() == number && !Objects.equals(t.getId(), excludeId));
        if (taken) {
            throw new ValidationBusinessException("Another active table already uses number " + number);
        }
    }

    private RestaurantTable find(Long id) {
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        access.requireSameRestaurant(table.getRestaurant().getId(), "Table not found");
        return table;
    }

    private void changed() {
        realtime.broadcast(access.restaurantId(), RealtimeService.TOPIC_TABLES);
    }

    private static final Comparator<RestaurantTable> ORDER = Comparator
        .comparingInt(RestaurantTable::getSortOrder).thenComparingInt(RestaurantTable::getTableNumber);

    static TableResponse toResponse(RestaurantTable table) {
        return new TableResponse(
            table.getId(),
            table.getTableNumber(),
            table.getName(),
            table.getFloor(),
            table.getCapacity(),
            table.getPrice(),
            table.getRestaurant().getId(),
            table.isActive(),
            table.getZone() != null ? table.getZone().getId() : null,
            table.getZone() != null ? table.getZone().getName() : null,
            table.getGridX(),
            table.getGridY(),
            table.getStatus().name(),
            table.getGroup() != null ? table.getGroup().getId() : null,
            table.getGroup() != null ? table.getGroup().getName() : null,
            table.getSortOrder()
        );
    }
}
