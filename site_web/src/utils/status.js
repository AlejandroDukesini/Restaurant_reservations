// Etiquetas y estilos para estados de pedido / plato.
export const ORDER_STATUS = {
  PENDING: { label: "Pendiente", chip: "chip-pending" },
  IN_PROGRESS: { label: "En preparación", chip: "chip-progress" },
  COMPLETED: { label: "Completado", chip: "chip-ready" },
  CANCELLED: { label: "Cancelado", chip: "chip-muted" }
};

export const ITEM_STATUS = {
  PENDING: { label: "Pendiente", chip: "chip-pending" },
  PREPARING: { label: "Preparando", chip: "chip-progress" },
  READY: { label: "Listo", chip: "chip-ready" }
};

export const CATEGORY_LABEL = {
  STARTER: "Entrada",
  MAIN: "Plato fuerte",
  DESSERT: "Postre",
  DRINK: "Bebida"
};

export function money(value) {
  return `$${Number(value ?? 0).toFixed(2)}`;
}
