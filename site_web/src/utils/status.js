// Etiquetas y estilos para estados de pedido / plato / mesa / reserva.
export const ORDER_STATUS = {
  PENDING: { label: "En cocina", chip: "chip-pending" },
  PREPARING: { label: "En preparación", chip: "chip-progress" },
  READY: { label: "Listo", chip: "chip-ready" },
  PACKING: { label: "Empacando", chip: "chip-progress" },
  PACKED: { label: "Empacado", chip: "chip-warn" },
  ON_THE_WAY: { label: "En camino", chip: "chip-progress" },
  DELIVERED: { label: "Entregado", chip: "chip-muted" },
  CANCELLED: { label: "Cancelado", chip: "chip-danger" },
  // Valores del sistema anterior (pedidos históricos de clientes antiguos).
  IN_PROGRESS: { label: "En preparación", chip: "chip-progress" },
  COMPLETED: { label: "Completado", chip: "chip-ready" }
};

export const ITEM_STATUS = {
  PENDING: { label: "Pendiente", chip: "chip-pending" },
  PREPARING: { label: "Preparando", chip: "chip-progress" },
  READY: { label: "Listo", chip: "chip-ready" }
};

export const TABLE_STATE = {
  AVAILABLE: "Disponible",
  OCCUPIED: "Ocupada",
  SENT: "Pedido enviado",
  PREPARING: "En preparación",
  READY: "Pedido listo",
  DELIVERED: "Entregado",
  PAYMENT_PENDING: "Pendiente de pago",
  ATTENTION: "Requiere atención",
  MAINTENANCE: "Fuera de servicio"
};

export const RESERVATION_STATUS = {
  PENDING: { label: "Pendiente", chip: "chip-pending" },
  CONFIRMED: { label: "Confirmada", chip: "chip-ready" },
  CANCELLED: { label: "Cancelada", chip: "chip-muted" },
  COMPLETED: { label: "Completada", chip: "chip-progress" }
};

// Códigos del sistema anterior; las categorías nuevas ya llegan con su nombre.
export const CATEGORY_LABEL = {
  STARTER: "Entrada",
  MAIN: "Plato fuerte",
  DESSERT: "Postre",
  DRINK: "Bebida"
};

export const ROLE_LABEL = {
  ADMIN: "Gerente",
  EMPLOYEE: "Mesero",
  COOK: "Cocina",
  DELIVERY: "Domiciliario"
};

export const PERMISSION_LABEL = {
  VIEW_METRICS: "Ver métricas",
  MANAGE_TABLES: "Administrar mesas",
  TOGGLE_AVAILABILITY: "Marcar productos agotados",
  MANAGE_DELIVERIES: "Gestionar domicilios",
  CANCEL_ORDERS: "Cancelar pedidos ajenos",
  KITCHEN_ACCESS: "Acceso a cocina"
};

export function money(value) {
  return `$${Number(value ?? 0).toFixed(2)}`;
}

/** Minutos transcurridos entre una fecha ISO del servidor (hora local) y `now`. */
export function minutesSince(iso, now = Date.now()) {
  if (!iso) return null;
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return null;
  return Math.max(0, Math.floor((now - t) / 60000));
}

export function formatMinutes(value) {
  if (value === null || value === undefined) return "—";
  if (value < 60) return `${Math.round(value * 10) / 10} min`;
  const h = Math.floor(value / 60);
  const m = Math.round(value % 60);
  return `${h} h ${m} min`;
}

export function timeOf(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "" : d.toLocaleTimeString("es", { hour: "2-digit", minute: "2-digit" });
}

/** Identificador único para la cabecera Idempotency-Key. */
export function newRequestKey() {
  return crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
