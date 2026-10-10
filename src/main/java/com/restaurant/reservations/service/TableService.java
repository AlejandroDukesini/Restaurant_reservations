package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.TableRequest;
import com.restaurant.reservations.dto.TableResponse;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Zone;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.TableRepository;
import com.restaurant.reservations.repository.ZoneRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableService {

    private static final Logger log = LoggerFactory.getLogger(TableService.class);

    private final TableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final ZoneRepository zoneRepository;
    private final AuthService authService;

    public TableService(
        TableRepository tableRepository,
        RestaurantRepository restaurantRepository,
        ZoneRepository zoneRepository,
        AuthService authService
    ) {
        this.tableRepository = tableRepository;
        this.restaurantRepository = restaurantRepository;
        this.zoneRepository = zoneRepository;
        this.authService = authService;
    }

    @Transactional
    public TableResponse createTable(TableRequest request) {
        Long restaurantId = authService.getCurrentUserRestaurantId();
        if (restaurantId == null) {
            throw new IllegalArgumentException("User not associated with a restaurant");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));

        Zone zone = null;
        if (request.zoneId() != null) {
            zone = zoneRepository.findByIdAndRestaurantId(request.zoneId(), restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found for restaurant"));
        }

        RestaurantTable table = new RestaurantTable(
            request.tableNumber(),
            request.floor(),
            request.capacity(),
            request.price(),
            restaurant
        );
        table.setName(request.name());
        table.setZone(zone);
        table.setGridX(request.gridX());
        table.setGridY(request.gridY());

        return toResponse(tableRepository.save(table));
    }

    public List<TableResponse> getTablesByRestaurant(Long restaurantId) {
        return tableRepository.findByRestaurantIdAndActiveTrue(restaurantId).stream()
            .map(TableService::toResponse)
            .toList();
    }

    public List<TableResponse> getMyTables(Integer floor) {
        Long restaurantId = authService.getCurrentUserRestaurantId();
        if (restaurantId == null) {
            throw new IllegalArgumentException("User not associated with a restaurant");
        }
        return floor != null ? getTablesByFloor(restaurantId, floor) : getTablesByRestaurant(restaurantId);
    }

    public List<TableResponse> getTablesByFloor(Long restaurantId, int floor) {
        return tableRepository.findByRestaurantIdAndFloorAndActiveTrue(restaurantId, floor).stream()
            .map(TableService::toResponse)
            .toList();
    }

    public TableResponse getTableById(Long id) {
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        // Faltaba el control de tenant: cualquier mesero podia leer mesas
        // (numero, capacidad, precio) de otros restaurantes iterando el id.
        requireOwnRestaurant(table.getRestaurant().getId());
        return toResponse(table);
    }

    @Transactional
    public TableResponse updateTable(Long id, TableRequest request) {
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Table not found"));

        Long restaurantId = requireOwnRestaurant(table.getRestaurant().getId());

        Zone zone = null;
        if (request.zoneId() != null) {
            zone = zoneRepository.findByIdAndRestaurantId(request.zoneId(), restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found for restaurant"));
        }

        table.setTableNumber(request.tableNumber());
        table.setName(request.name());
        table.setFloor(request.floor());
        table.setCapacity(request.capacity());
        table.setPrice(request.price());
        table.setZone(zone);
        table.setGridX(request.gridX());
        table.setGridY(request.gridY());

        return toResponse(tableRepository.save(table));
    }

    @Transactional
    public void deleteTable(Long id) {
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Table not found"));

        requireOwnRestaurant(table.getRestaurant().getId());

        table.setActive(false);
        tableRepository.save(table);
    }

    /**
     * Exige que el recurso pertenezca al restaurante del usuario autenticado y
     * devuelve ese id. Responde 404 para no confirmar la existencia de mesas ajenas.
     */
    private Long requireOwnRestaurant(Long ownerRestaurantId) {
        Long callerRestaurantId = authService.getCurrentUserRestaurantId();
        if (callerRestaurantId == null) {
            throw new ValidationBusinessException("User not associated with a restaurant");
        }
        if (!callerRestaurantId.equals(ownerRestaurantId)) {
            log.warn(
                "Acceso cross-tenant a mesa denegado: userId={} (restaurante {}) pidio recurso del restaurante {}",
                authService.getCurrentUserId(), callerRestaurantId, ownerRestaurantId
            );
            throw new ResourceNotFoundException("Table not found");
        }
        return callerRestaurantId;
    }

    private static TableResponse toResponse(RestaurantTable table) {
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
            table.getStatus().name()
        );
    }
}
