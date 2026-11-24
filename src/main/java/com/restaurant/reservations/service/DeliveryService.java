package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OpsDtos.AssignDriverRequest;
import com.restaurant.reservations.dto.OpsDtos.DeliveryOrderRequest;
import com.restaurant.reservations.dto.OpsDtos.DriverRequest;
import com.restaurant.reservations.dto.OpsDtos.DriverResponse;
import com.restaurant.reservations.dto.OpsDtos.PlatformRequest;
import com.restaurant.reservations.dto.OpsDtos.PlatformResponse;
import com.restaurant.reservations.dto.OrderResponse;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.StateConflictException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.DeliveryDriver;
import com.restaurant.reservations.model.DeliveryPlatform;
import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.OrderChannel;
import com.restaurant.reservations.model.OrderStatus;
import com.restaurant.reservations.model.PaymentStatus;
import com.restaurant.reservations.model.Permission;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.DeliveryDriverRepository;
import com.restaurant.reservations.repository.DeliveryPlatformRepository;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domicilios: flujo independiente de las mesas que comparte menu, cocina, metricas y personal.
 *
 * PENDING -> PREPARING -> READY (cocina) -> PACKING -> PACKED (empaque) -> ON_THE_WAY -> DELIVERED.
 * "Esperando domiciliario" = PACKED sin domiciliario; "Asignado" = con domiciliario y aun sin salir.
 *
 * Un domiciliario con cuenta solo ve y mueve SUS domicilios, y solo los pasos de reparto
 * (salida y entrega). No ve el resto de pedidos ni datos de otros clientes.
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final List<OrderStatus> FINAL = List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;
    private final DeliveryPlatformRepository platformRepository;
    private final DeliveryDriverRepository driverRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final KitchenService kitchenService;
    private final AccessService access;
    private final NotificationService notifications;
    private final RealtimeService realtime;

    public DeliveryService(
        OrderRepository orderRepository,
        DeliveryPlatformRepository platformRepository,
        DeliveryDriverRepository driverRepository,
        UserRepository userRepository,
        OrderService orderService,
        KitchenService kitchenService,
        AccessService access,
        NotificationService notifications,
        RealtimeService realtime
    ) {
        this.orderRepository = orderRepository;
        this.platformRepository = platformRepository;
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
        this.orderService = orderService;
        this.kitchenService = kitchenService;
        this.access = access;
        this.notifications = notifications;
        this.realtime = realtime;
    }

    // ------------------------------------------------------------------ Pedidos

    @Transactional
    public OrderResponse create(DeliveryOrderRequest request) {
        access.require(Permission.MANAGE_DELIVERIES);
        User employee = access.user();
        Order order = new Order(access.restaurantId(), employee, OrderService.blankToNull(request.notes()));
        order.setCustomerName(OrderService.blankToNull(request.customerName()));
        order.setCustomerPhone(OrderService.blankToNull(request.customerPhone()));
        order.setDeliveryAddress(OrderService.blankToNull(request.deliveryAddress()));
        order.setExternalRef(OrderService.blankToNull(request.externalRef()));
        if (request.platformId() != null) {
            order.setPlatform(activePlatform(request.platformId()));
        }
        if (request.driverId() != null) {
            order.setDriver(activeDriver(request.driverId()));
            order.setDriverAssignedAt(LocalDateTime.now());
        }
        Order saved = orderService.persistWithItems(order, request.items(), employee);
        if (saved.getDriver() != null) {
            notifyDriver(saved, "Domicilio asignado");
        }
        return OrderMapper.toResponse(saved);
    }

    /** Tablero: domicilios activos y los cerrados en las ultimas 12 horas. */
    @Transactional(readOnly = true)
    public List<OrderResponse> board() {
        access.require(Permission.MANAGE_DELIVERIES);
        return orderRepository.findChannelBoard(access.restaurantId(), OrderChannel.DELIVERY, FINAL,
                LocalDateTime.now().minusHours(12)).stream()
            .map(OrderMapper::toResponse).toList();
    }

    /** Vista del domiciliario: solo sus domicilios. */
    @Transactional(readOnly = true)
    public List<OrderResponse> mine() {
        DeliveryDriver driver = driverRepository.findByUserId(access.userId())
            .filter(d -> d.getRestaurantId().equals(access.restaurantId()))
            .orElse(null);
        if (driver == null) {
            return List.of();
        }
        return orderRepository.findDriverBoard(driver.getId(), FINAL, LocalDateTime.now().minusHours(12)).stream()
            .map(OrderMapper::toResponse).toList();
    }

    @Transactional
    public OrderResponse startPacking(Long id) {
        requirePacker();
        Order order = delivery(id);
        if (order.getStatus() != OrderStatus.READY) {
            throw new StateConflictException("Only an order ready in the kitchen can be packed");
        }
        order.setStatus(OrderStatus.PACKING);
        order.setPackingAt(LocalDateTime.now());
        return save(order, "PACKING", "Inicio de empaque");
    }

    @Transactional
    public OrderResponse finishPacking(Long id) {
        requirePacker();
        Order order = delivery(id);
        if (order.getStatus() != OrderStatus.PACKING) {
            throw new StateConflictException("The order is not being packed");
        }
        order.setStatus(OrderStatus.PACKED);
        order.setPackedAt(LocalDateTime.now());
        OrderResponse response = save(order, "PACKED", "Empacado");
        if (order.getDriver() != null) {
            notifyDriver(order, "Domicilio listo para recoger");
        } else {
            notifications.notify(order.getRestaurantId(), List.of(order.getEmployee().getId()), "DELIVERY_WAITING",
                "Esperando domiciliario · " + OrderMapper.label(order), null, "ORDER", order.getId());
        }
        return response;
    }

    @Transactional
    public OrderResponse assignDriver(Long id, AssignDriverRequest request) {
        access.require(Permission.MANAGE_DELIVERIES);
        Order order = delivery(id);
        if (order.getStatus().isFinal() || order.getStatus() == OrderStatus.ON_THE_WAY) {
            throw new StateConflictException("The driver can no longer be changed");
        }
        if (request.driverId() == null) {
            order.setDriver(null);
            order.setDriverAssignedAt(null);
            return save(order, "DRIVER_UNASSIGNED", "Domiciliario retirado");
        }
        DeliveryDriver driver = activeDriver(request.driverId());
        if (order.getDriver() != null && order.getDriver().getId().equals(driver.getId())) {
            return OrderMapper.toResponse(order);
        }
        order.setDriver(driver);
        order.setDriverAssignedAt(LocalDateTime.now());
        OrderResponse response = save(order, "DRIVER_ASSIGNED", "Asignado a " + driver.getName());
        notifyDriver(order, "Domicilio asignado");
        return response;
    }

    @Transactional
    public OrderResponse dispatch(Long id) {
        Order order = delivery(id);
        requireDriverOrManager(order);
        if (order.getStatus() != OrderStatus.PACKED) {
            throw new StateConflictException("Only a packed order can leave");
        }
        if (order.getDriver() == null) {
            throw new StateConflictException("Assign a driver first");
        }
        order.setStatus(OrderStatus.ON_THE_WAY);
        order.setDispatchedAt(LocalDateTime.now());
        return save(order, "ON_THE_WAY", "Salio con " + order.getDriver().getName());
    }

    @Transactional
    public OrderResponse deliver(Long id) {
        Order order = delivery(id);
        requireDriverOrManager(order);
        if (order.getStatus() != OrderStatus.ON_THE_WAY) {
            throw new StateConflictException("Only an order on the way can be delivered");
        }
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        // El cobro de un domicilio se da por realizado al entregar (efectivo o plataforma).
        order.setPaymentStatus(PaymentStatus.PAID);
        OrderResponse response = save(order, "DELIVERED", "Entregado al cliente");
        notifications.notify(order.getRestaurantId(), List.of(order.getEmployee().getId()), "DELIVERY_DONE",
            "Domicilio entregado · " + OrderMapper.label(order), null, "ORDER", order.getId());
        return response;
    }

    // ------------------------------------------------------------------ Plataformas

    @Transactional(readOnly = true)
    public List<PlatformResponse> platforms() {
        if (!access.can(Permission.MANAGE_DELIVERIES)) {
            throw new AccessDeniedException("Access denied");
        }
        return platformRepository.findByRestaurantIdOrderByNameAsc(access.restaurantId()).stream()
            .map(p -> new PlatformResponse(p.getId(), p.getName(), p.isActive())).toList();
    }

    @Transactional
    public PlatformResponse savePlatform(Long id, PlatformRequest request) {
        access.requireManager();
        String name = request.name().strip();
        boolean duplicate = platformRepository.findByRestaurantIdOrderByNameAsc(access.restaurantId()).stream()
            .anyMatch(p -> p.getName().equalsIgnoreCase(name) && !Objects.equals(p.getId(), id));
        if (duplicate) {
            throw new ValidationBusinessException("A platform with that name already exists");
        }
        DeliveryPlatform platform = id == null ? new DeliveryPlatform(access.restaurantId(), name)
            : platformRepository.findByIdAndRestaurantId(id, access.restaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Platform not found"));
        platform.setName(name);
        if (request.active() != null) {
            platform.setActive(request.active());
        }
        platformRepository.save(platform);
        realtime.broadcast(access.restaurantId(), RealtimeService.TOPIC_DELIVERIES);
        return new PlatformResponse(platform.getId(), platform.getName(), platform.isActive());
    }

    // ------------------------------------------------------------------ Domiciliarios

    @Transactional(readOnly = true)
    public List<DriverResponse> drivers() {
        if (!access.can(Permission.MANAGE_DELIVERIES)) {
            throw new AccessDeniedException("Access denied");
        }
        List<DriverResponse> result = new ArrayList<>();
        for (DeliveryDriver d : driverRepository.findByRestaurantIdOrderByNameAsc(access.restaurantId())) {
            long active = orderRepository.findDriverBoard(d.getId(), FINAL, LocalDateTime.now()).size();
            result.add(new DriverResponse(d.getId(), d.getName(), d.getPhone(),
                d.getUser() != null ? d.getUser().getId() : null,
                d.getUser() != null ? d.getUser().getEmail() : null, d.isActive(), active));
        }
        return result;
    }

    /**
     * Alta o edicion de domiciliario. Si se vincula a una cuenta, debe ser un usuario ACTIVO del
     * mismo restaurante con rol DELIVERY, y no estar ya vinculado a otro domiciliario.
     */
    @Transactional
    public DriverResponse saveDriver(Long id, DriverRequest request) {
        access.requireManager();
        Long restaurantId = access.restaurantId();
        DeliveryDriver driver = id == null ? new DeliveryDriver(restaurantId, request.name().strip())
            : driverRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
        driver.setName(request.name().strip());
        driver.setPhone(OrderService.blankToNull(request.phone()));
        if (request.userId() != null) {
            User user = userRepository.findByIdAndRestaurantId(request.userId(), restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            if (!user.isActive() || user.getRole() != Role.DELIVERY) {
                throw new ValidationBusinessException("The linked account must be an active delivery user");
            }
            driverRepository.findByUserId(user.getId())
                .filter(other -> !Objects.equals(other.getId(), driver.getId()))
                .ifPresent(other -> {
                    throw new ValidationBusinessException("That account is already linked to another driver");
                });
            driver.setUser(user);
        } else {
            driver.setUser(null);
        }
        if (request.active() != null) {
            driver.setActive(request.active());
        }
        driverRepository.save(driver);
        log.info("Domiciliario {} guardado por userId={}", driver.getId(), access.userId());
        realtime.broadcast(restaurantId, RealtimeService.TOPIC_DELIVERIES);
        return new DriverResponse(driver.getId(), driver.getName(), driver.getPhone(),
            driver.getUser() != null ? driver.getUser().getId() : null,
            driver.getUser() != null ? driver.getUser().getEmail() : null, driver.isActive(), 0);
    }

    // ------------------------------------------------------------------ Utilidades

    private Order delivery(Long id) {
        Order order = orderService.lockedOrder(id);
        if (!order.isDelivery()) {
            throw new ResourceNotFoundException("Delivery not found");
        }
        return order;
    }

    private OrderResponse save(Order order, String event, String detail) {
        orderRepository.save(order);
        orderService.record(order, event, detail, access.user());
        orderService.changed(order);
        return OrderMapper.toResponse(order);
    }

    /** Empacan: cocina, gerente o quien gestiona domicilios. */
    private void requirePacker() {
        if (access.can(Permission.MANAGE_DELIVERIES)) {
            return;
        }
        kitchenService.requireKitchen();
    }

    /** Reparto: el domiciliario asignado (por su cuenta) o quien gestiona domicilios. */
    private void requireDriverOrManager(Order order) {
        if (OrderService.isAssignedDriver(order, access.userId())) {
            return;
        }
        if (access.role() == Role.DELIVERY) {
            // Domicilio de otro domiciliario: no se confirma que exista.
            throw new ResourceNotFoundException("Delivery not found");
        }
        access.require(Permission.MANAGE_DELIVERIES);
    }

    private DeliveryPlatform activePlatform(Long id) {
        DeliveryPlatform platform = platformRepository.findByIdAndRestaurantId(id, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Platform not found"));
        if (!platform.isActive()) {
            throw new ValidationBusinessException("The platform is inactive");
        }
        return platform;
    }

    private DeliveryDriver activeDriver(Long id) {
        DeliveryDriver driver = driverRepository.findByIdAndRestaurantId(id, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
        if (!driver.isActive() || (driver.getUser() != null && !driver.getUser().isActive())) {
            throw new ValidationBusinessException("The driver is inactive");
        }
        return driver;
    }

    private void notifyDriver(Order order, String title) {
        if (order.getDriver() != null && order.getDriver().getUser() != null) {
            notifications.notify(order.getRestaurantId(), List.of(order.getDriver().getUser().getId()),
                "DELIVERY_ASSIGNED", title + " · " + OrderMapper.label(order), order.getDeliveryAddress(), "ORDER",
                order.getId());
        }
    }
}
