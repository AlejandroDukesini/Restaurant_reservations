import { apiFetch } from "./client";

export const fetchOrdersByTable = (tableId) =>
  apiFetch(`/api/employee/tables/${tableId}/orders`);

export const createOrder = (payload) =>
  apiFetch("/api/employee/orders", { method: "POST", body: payload });

export const updateOrderStatus = (id, status) =>
  apiFetch(`/api/employee/orders/${id}/status`, { method: "PUT", params: { status } });

export const deleteOrder = (id) =>
  apiFetch(`/api/employee/orders/${id}`, { method: "DELETE" });

// Admin
export const fetchAllOrders = () => apiFetch("/api/admin/orders");

// Cocina
export const fetchKitchenQueue = () => apiFetch("/api/cook/queue");

export const updateItemStatus = (orderItemId, status) =>
  apiFetch(`/api/cook/order-items/${orderItemId}/status`, {
    method: "PUT",
    params: { status }
  });
