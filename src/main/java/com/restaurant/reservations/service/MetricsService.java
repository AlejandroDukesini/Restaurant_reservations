package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OpsDtos.ChannelTimes;
import com.restaurant.reservations.dto.OpsDtos.DailyPoint;
import com.restaurant.reservations.dto.OpsDtos.EmployeeStat;
import com.restaurant.reservations.dto.OpsDtos.MetricsResponse;
import com.restaurant.reservations.dto.OpsDtos.NamedAmount;
import com.restaurant.reservations.dto.OpsDtos.TimeStat;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.OrderItem;
import com.restaurant.reservations.model.OrderStatus;
import com.restaurant.reservations.model.PaymentStatus;
import com.restaurant.reservations.model.Permission;
import com.restaurant.reservations.model.TableSession;
import com.restaurant.reservations.model.TableSessionStatus;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.repository.TableSessionRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metricas calculadas exclusivamente con los datos guardados (marcas de tiempo del servidor
 * y pedidos reales). Cada tiempo es una diferencia entre dos eventos concretos; si falta uno
 * de los dos (p. ej. pedidos anteriores a V2) ese pedido no entra en el promedio, y "count"
 * indica cuantos pedidos sustentan cada cifra.
 *
 * Definiciones (minutos):
 *  Mesa:  espera en cocina = registro -> inicio de preparacion
 *         preparacion      = inicio -> listo
 *         pedido a listo   = registro -> listo
 *         listo a entrega  = listo -> entregado en mesa   (servicio del mesero)
 *         total del pedido = registro -> entregado
 *         primera orden    = mesa ocupada -> primer pedido (atencion inicial)
 *         cierre tardio    = ultima entrega -> cierre de la mesa
 *         estancia         = mesa ocupada -> cierre
 *  Domicilio: preparacion, empaque (inicio -> fin de empaque), espera de domiciliario
 *         (empacado -> salida), reparto (salida -> entrega) y total (registro -> entrega).
 *
 * Ingreso definitivo = pedidos COBRADOS (PAID) y no cancelados. Lo pendiente de cobro se
 * informa aparte y los cancelados nunca suman.
 */
@Service
public class MetricsService {

    private static final int MAX_RANGE_DAYS = 366;

    private final OrderRepository orderRepository;
    private final TableSessionRepository sessionRepository;
    private final AccessService access;

    public MetricsService(OrderRepository orderRepository, TableSessionRepository sessionRepository,
                          AccessService access) {
        this.orderRepository = orderRepository;
        this.sessionRepository = sessionRepository;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public MetricsResponse compute(String period, LocalDate fromDate, LocalDate toDate) {
        access.require(Permission.VIEW_METRICS);
        LocalDateTime[] range = range(period, fromDate, toDate);
        LocalDateTime from = range[0];
        LocalDateTime to = range[1];
        Long restaurantId = access.restaurantId();

        List<Order> orders = orderRepository.findForMetrics(restaurantId, from, to);
        List<TableSession> sessions = sessionRepository
            .findByRestaurantIdAndOpenedAtGreaterThanEqualAndOpenedAtLessThan(restaurantId, from, to);

        List<Order> valid = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();
        List<Order> paid = valid.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.PAID).toList();
        List<Order> dineIn = valid.stream().filter(o -> !o.isDelivery()).toList();
        List<Order> delivery = valid.stream().filter(Order::isDelivery).toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        double revenue = sum(paid);
        summary.put("orders", valid.size());
        summary.put("completedOrders", valid.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count());
        summary.put("activeOrders", valid.stream().filter(o -> !o.getStatus().isFinal()).count());
        summary.put("cancelledOrders", orders.size() - valid.size());
        summary.put("dineInOrders", dineIn.size());
        summary.put("deliveryOrders", delivery.size());
        summary.put("tablesServed", sessions.stream().filter(s -> s.getStatus() == TableSessionStatus.CLOSED).count());
        summary.put("tablesOpen", sessions.stream().filter(TableSession::isOpen).count());
        summary.put("revenue", round(revenue));
        summary.put("pendingRevenue", round(sum(valid.stream()
            .filter(o -> o.getPaymentStatus() != PaymentStatus.PAID).toList())));
        summary.put("averageTicket", paid.isEmpty() ? null : round(revenue / paid.size()));
        long withEstimate = valid.stream().filter(o -> o.getEstimatedMinutes() != null && o.getReadyAt() != null).count();
        long late = valid.stream().filter(MetricsService::isLate).count();
        summary.put("lateOrders", late);
        summary.put("lateRate", withEstimate == 0 ? null : round(100.0 * late / withEstimate));
        summary.put("estimatedAvgMinutes", avg(valid.stream().filter(o -> o.getReadyAt() != null)
            .map(Order::getEstimatedMinutes).filter(Objects::nonNull).map(Integer::doubleValue).toList()));
        summary.put("realOrderToReadyAvgMinutes", stat(valid.stream()
            .filter(o -> o.getEstimatedMinutes() != null).toList(), Order::getOrderDate, Order::getReadyAt).avgMinutes());

        Map<String, TimeStat> dineTimes = new LinkedHashMap<>();
        dineTimes.put("kitchenQueue", stat(dineIn, Order::getOrderDate, Order::getPreparingAt));
        dineTimes.put("preparation", stat(dineIn, Order::getPreparingAt, Order::getReadyAt));
        dineTimes.put("orderToReady", stat(dineIn, Order::getOrderDate, Order::getReadyAt));
        dineTimes.put("readyToDelivered", stat(dineIn, Order::getReadyAt, Order::getDeliveredAt));
        dineTimes.put("orderToDelivered", stat(dineIn, Order::getOrderDate, Order::getDeliveredAt));
        dineTimes.putAll(sessionTimes(sessions, dineIn));

        Map<String, TimeStat> deliveryTimes = new LinkedHashMap<>();
        deliveryTimes.put("preparation", stat(delivery, Order::getPreparingAt, Order::getReadyAt));
        deliveryTimes.put("orderToReady", stat(delivery, Order::getOrderDate, Order::getReadyAt));
        deliveryTimes.put("packing", stat(delivery, Order::getPackingAt, Order::getPackedAt));
        deliveryTimes.put("waitingDriver", stat(delivery, Order::getPackedAt, Order::getDispatchedAt));
        deliveryTimes.put("transit", stat(delivery, Order::getDispatchedAt, Order::getDeliveredAt));
        deliveryTimes.put("total", stat(delivery, Order::getOrderDate, Order::getDeliveredAt));

        Map<String, Object> previous = previousPeriod(restaurantId, from, to);

        return new MetricsResponse(
            from, to, summary, dineTimes, deliveryTimes,
            topProducts(valid, paid),
            categories(valid, paid),
            amounts(paid.stream().filter(o -> !o.isDelivery()).toList(), OrderMapper::label),
            amounts(paid, o -> o.isDelivery() ? "Domicilio" : "Mesa"),
            amounts(paid.stream().filter(Order::isDelivery).toList(),
                o -> o.getPlatform() != null ? o.getPlatform().getName() : "Sin plataforma"),
            channelTimes(delivery, o -> o.getPlatform() != null ? o.getPlatform().getName() : "Sin plataforma"),
            channelTimes(delivery.stream().filter(o -> o.getDriver() != null).toList(), o -> o.getDriver().getName()),
            employees(valid),
            trend(valid, paid),
            previous,
            bottlenecks(dineTimes, deliveryTimes)
        );
    }

    // ------------------------------------------------------------------ Calculos

    static TimeStat stat(List<Order> orders, Function<Order, LocalDateTime> start, Function<Order, LocalDateTime> end) {
        List<Double> values = new ArrayList<>();
        for (Order order : orders) {
            Double minutes = minutes(start.apply(order), end.apply(order));
            if (minutes != null) {
                values.add(minutes);
            }
        }
        return toStat(values);
    }

    private static TimeStat toStat(List<Double> values) {
        if (values.isEmpty()) {
            return new TimeStat(null, null, 0);
        }
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        return new TimeStat(avg(values), round(max), values.size());
    }

    private Map<String, TimeStat> sessionTimes(List<TableSession> sessions, List<Order> dineIn) {
        Map<Long, List<Order>> bySession = dineIn.stream().filter(o -> o.getSession() != null)
            .collect(Collectors.groupingBy(o -> o.getSession().getId()));
        List<Double> firstOrder = new ArrayList<>();
        List<Double> lateClose = new ArrayList<>();
        List<Double> stay = new ArrayList<>();
        for (TableSession session : sessions) {
            List<Order> orders = bySession.getOrDefault(session.getId(), List.of());
            orders.stream().map(Order::getOrderDate).min(Comparator.naturalOrder())
                .map(first -> minutes(session.getOpenedAt(), first)).ifPresent(firstOrder::add);
            if (session.getClosedAt() != null) {
                Double total = minutes(session.getOpenedAt(), session.getClosedAt());
                if (total != null) {
                    stay.add(total);
                }
                orders.stream().map(Order::getDeliveredAt).filter(Objects::nonNull).max(Comparator.naturalOrder())
                    .map(last -> minutes(last, session.getClosedAt())).filter(Objects::nonNull).ifPresent(lateClose::add);
            }
        }
        Map<String, TimeStat> result = new LinkedHashMap<>();
        result.put("seatToFirstOrder", toStat(firstOrder));
        result.put("deliveredToClose", toStat(lateClose));
        result.put("tableStay", toStat(stay));
        return result;
    }

    private static List<NamedAmount> topProducts(List<Order> valid, List<Order> paid) {
        Map<String, long[]> counts = new HashMap<>();
        Map<String, Double> amounts = new HashMap<>();
        for (Order order : valid) {
            for (OrderItem item : order.getItems()) {
                counts.computeIfAbsent(item.getItemName(), k -> new long[1])[0] += item.getQuantity();
            }
        }
        for (Order order : paid) {
            for (OrderItem item : order.getItems()) {
                amounts.merge(item.getItemName(), item.getPrice() * item.getQuantity(), Double::sum);
            }
        }
        return counts.entrySet().stream()
            .map(e -> new NamedAmount(e.getKey(), e.getValue()[0], round(amounts.getOrDefault(e.getKey(), 0.0))))
            .sorted(Comparator.comparingLong(NamedAmount::count).reversed().thenComparing(NamedAmount::name))
            .limit(15).toList();
    }

    private static List<NamedAmount> categories(List<Order> valid, List<Order> paid) {
        Map<String, long[]> counts = new HashMap<>();
        Map<String, Double> amounts = new HashMap<>();
        for (Order order : valid) {
            for (OrderItem item : order.getItems()) {
                counts.computeIfAbsent(category(item), k -> new long[1])[0] += item.getQuantity();
            }
        }
        for (Order order : paid) {
            for (OrderItem item : order.getItems()) {
                amounts.merge(category(item), item.getPrice() * item.getQuantity(), Double::sum);
            }
        }
        return counts.entrySet().stream()
            .map(e -> new NamedAmount(e.getKey(), e.getValue()[0], round(amounts.getOrDefault(e.getKey(), 0.0))))
            .sorted(Comparator.comparingDouble(NamedAmount::amount).reversed().thenComparing(NamedAmount::name))
            .toList();
    }

    private static String category(OrderItem item) {
        return item.getCategoryName() != null ? item.getCategoryName() : "Sin categoria";
    }

    private static List<NamedAmount> amounts(List<Order> orders, Function<Order, String> key) {
        Map<String, List<Order>> grouped = orders.stream().collect(Collectors.groupingBy(key));
        return grouped.entrySet().stream()
            .map(e -> new NamedAmount(e.getKey(), e.getValue().size(), round(sum(e.getValue()))))
            .sorted(Comparator.comparingDouble(NamedAmount::amount).reversed())
            .toList();
    }

    private static List<ChannelTimes> channelTimes(List<Order> orders, Function<Order, String> key) {
        Map<String, List<Order>> grouped = orders.stream().collect(Collectors.groupingBy(key));
        return grouped.entrySet().stream()
            .map(e -> new ChannelTimes(e.getKey(), e.getValue().size(),
                stat(e.getValue(), Order::getOrderDate, Order::getDeliveredAt).avgMinutes(),
                stat(e.getValue(), Order::getDispatchedAt, Order::getDeliveredAt).avgMinutes()))
            .sorted(Comparator.comparingLong(ChannelTimes::orders).reversed())
            .toList();
    }

    private static List<EmployeeStat> employees(List<Order> valid) {
        Map<Long, List<Order>> grouped = valid.stream().collect(Collectors.groupingBy(o -> o.getEmployee().getId()));
        return grouped.values().stream()
            .map(list -> new EmployeeStat(list.get(0).getEmployee().getId(), list.get(0).getEmployee().getName(),
                list.size(),
                stat(list.stream().filter(o -> !o.isDelivery()).toList(), Order::getReadyAt, Order::getDeliveredAt)
                    .avgMinutes(),
                round(sum(list.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.PAID).toList()))))
            .sorted(Comparator.comparingLong(EmployeeStat::orders).reversed())
            .toList();
    }

    private static List<DailyPoint> trend(List<Order> valid, List<Order> paid) {
        Map<LocalDate, long[]> counts = new TreeMap<>();
        Map<LocalDate, Double> revenue = new HashMap<>();
        for (Order order : valid) {
            counts.computeIfAbsent(order.getOrderDate().toLocalDate(), k -> new long[1])[0]++;
        }
        for (Order order : paid) {
            revenue.merge(order.getOrderDate().toLocalDate(), order.getTotalAmount(), Double::sum);
        }
        return counts.entrySet().stream()
            .map(e -> new DailyPoint(e.getKey().toString(), e.getValue()[0], round(revenue.getOrDefault(e.getKey(), 0.0))))
            .toList();
    }

    private Map<String, Object> previousPeriod(Long restaurantId, LocalDateTime from, LocalDateTime to) {
        Duration length = Duration.between(from, to);
        LocalDateTime prevFrom = from.minus(length);
        List<Order> orders = orderRepository.findForMetrics(restaurantId, prevFrom, from);
        List<Order> valid = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", prevFrom);
        result.put("to", from);
        result.put("orders", valid.size());
        result.put("revenue", round(sum(valid.stream().filter(o -> o.getPaymentStatus() == PaymentStatus.PAID).toList())));
        result.put("orderToReadyAvgMinutes", stat(valid, Order::getOrderDate, Order::getReadyAt).avgMinutes());
        return result;
    }

    /** Etapas ordenadas de mayor a menor tiempo promedio: la primera es el cuello de botella. */
    private static List<Map<String, Object>> bottlenecks(Map<String, TimeStat> dine, Map<String, TimeStat> delivery) {
        List<Map<String, Object>> stages = new ArrayList<>();
        addStage(stages, "DINE_IN", "kitchenQueue", "Cocina tarda en empezar", dine);
        addStage(stages, "DINE_IN", "preparation", "Preparacion en cocina", dine);
        addStage(stages, "DINE_IN", "readyToDelivered", "Mesero tarda en recoger/entregar", dine);
        addStage(stages, "DINE_IN", "seatToFirstOrder", "Pedido tarda en registrarse", dine);
        addStage(stages, "DINE_IN", "deliveredToClose", "Mesa abierta tras la entrega", dine);
        addStage(stages, "DELIVERY", "packing", "Empaque de domicilios", delivery);
        addStage(stages, "DELIVERY", "waitingDriver", "Espera de domiciliario", delivery);
        addStage(stages, "DELIVERY", "transit", "Reparto", delivery);
        stages.sort(Comparator.comparingDouble((Map<String, Object> m) -> (Double) m.get("avgMinutes")).reversed());
        return stages;
    }

    private static void addStage(List<Map<String, Object>> stages, String channel, String key, String label,
                                 Map<String, TimeStat> stats) {
        TimeStat stat = stats.get(key);
        if (stat != null && stat.avgMinutes() != null) {
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("channel", channel);
            stage.put("stage", key);
            stage.put("label", label);
            stage.put("avgMinutes", stat.avgMinutes());
            stage.put("count", stat.count());
            stages.add(stage);
        }
    }

    /** Retrasado: tardo mas que su estimado, o lleva mas que su estimado sin estar listo. */
    static boolean isLate(Order order) {
        Integer estimate = order.getEstimatedMinutes();
        if (estimate == null || estimate <= 0) {
            return false;
        }
        LocalDateTime end = order.getReadyAt();
        if (end == null) {
            if (!order.getStatus().isInKitchen()) {
                return false;
            }
            end = LocalDateTime.now();
        }
        Double minutes = minutes(order.getOrderDate(), end);
        return minutes != null && minutes > estimate;
    }

    static LocalDateTime[] range(String period, LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now();
        String p = period == null ? "today" : period;
        LocalDateTime from;
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        switch (p) {
            case "today" -> from = today.atStartOfDay();
            case "week" -> from = today.minusDays(6).atStartOfDay();
            case "month" -> from = today.minusDays(29).atStartOfDay();
            case "custom" -> {
                if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
                    throw new ValidationBusinessException("Invalid custom range");
                }
                if (fromDate.plusDays(MAX_RANGE_DAYS).isBefore(toDate)) {
                    throw new ValidationBusinessException("Range too long (max " + MAX_RANGE_DAYS + " days)");
                }
                from = fromDate.atStartOfDay();
                to = toDate.plusDays(1).atStartOfDay();
            }
            default -> throw new ValidationBusinessException("Invalid period");
        }
        return new LocalDateTime[] {from, to};
    }

    private static Double minutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return null;
        }
        return Duration.between(start, end).toMillis() / 60000.0;
    }

    private static Double avg(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static double sum(List<Order> orders) {
        return orders.stream().mapToDouble(Order::getTotalAmount).sum();
    }

    static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
