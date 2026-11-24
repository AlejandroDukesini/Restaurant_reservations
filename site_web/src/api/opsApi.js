import { apiFetch } from "./client";

// Endpoints de la plataforma operativa. Toda autorización la aplica el servidor.

// --- Personalización e imágenes
export const fetchBranding = (groupId) =>
  apiFetch("/api/branding", groupId ? { params: { groupId } } : {});
export const fetchRestaurantBranding = () => apiFetch("/api/admin/branding");
export const saveRestaurantBranding = (payload) =>
  apiFetch("/api/admin/branding", { method: "PUT", body: payload });
export const saveGroupBranding = (groupId, payload) =>
  apiFetch(`/api/admin/groups/${groupId}/branding`, { method: "PUT", body: payload });
export function uploadMedia(file) {
  const formData = new FormData();
  formData.append("file", file);
  return apiFetch("/api/admin/media", { method: "POST", formData });
}
export const deleteMedia = (id) => apiFetch(`/api/admin/media/${id}`, { method: "DELETE" });

// --- Perfil propio
export const fetchProfile = () => apiFetch("/api/me/profile");
export const saveProfile = (payload) => apiFetch("/api/me/profile", { method: "PUT", body: payload });
export function uploadAvatar(file) {
  const formData = new FormData();
  formData.append("file", file);
  return apiFetch("/api/me/avatar", { method: "POST", formData });
}
export const removeAvatar = () => apiFetch("/api/me/avatar", { method: "DELETE" });

// --- Grupos
export const fetchGroups = () => apiFetch("/api/groups");
export const createGroup = (payload) => apiFetch("/api/admin/groups", { method: "POST", body: payload });
export const updateGroup = (id, payload) => apiFetch(`/api/admin/groups/${id}`, { method: "PUT", body: payload });
export const deleteGroup = (id) => apiFetch(`/api/admin/groups/${id}`, { method: "DELETE" });
export const setGroupMembers = (id, userIds) =>
  apiFetch(`/api/admin/groups/${id}/members`, { method: "PUT", body: { userIds } });
export const fetchGroupActivity = (id) => apiFetch(`/api/admin/groups/${id}/activity`);

// --- Mensajería y notificaciones
export const fetchChatGroups = () => apiFetch("/api/chat/groups");
export const fetchMessages = (groupId, params) =>
  apiFetch(`/api/chat/groups/${groupId}/messages`, params ? { params } : {});
export const sendMessage = (groupId, payload) =>
  apiFetch(`/api/chat/groups/${groupId}/messages`, { method: "POST", body: payload });
export const markChatRead = (groupId) => apiFetch(`/api/chat/groups/${groupId}/read`, { method: "POST" });
export const fetchNotifications = () => apiFetch("/api/notifications");
export const fetchUnread = () => apiFetch("/api/notifications/unread");
export const markNotificationsRead = (id) =>
  apiFetch("/api/notifications/read", { method: "POST", ...(id ? { params: { id } } : {}) });

// --- Sala
export const fetchFloor = () => apiFetch("/api/floor/tables");
export const fetchSession = (tableId) => apiFetch(`/api/floor/tables/${tableId}/session`);
export const openTable = (tableId, guests) =>
  apiFetch(`/api/floor/tables/${tableId}/session`, { method: "POST", body: guests ? { guests } : {} });
export const requestAttention = (tableId) =>
  apiFetch(`/api/floor/tables/${tableId}/attention`, { method: "POST" });
export const clearAttention = (tableId) =>
  apiFetch(`/api/floor/tables/${tableId}/attention`, { method: "DELETE" });
export const requestBill = (tableId) => apiFetch(`/api/floor/tables/${tableId}/bill`, { method: "POST" });
export const closeTable = (tableId) => apiFetch(`/api/floor/tables/${tableId}/close`, { method: "POST" });

// --- Pedidos
export const createTableOrder = (payload, idempotencyKey) =>
  apiFetch("/api/orders", {
    method: "POST",
    body: payload,
    headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined
  });
export const fetchOrder = (id) => apiFetch(`/api/orders/${id}`);
export const fetchOrderHistory = (id) => apiFetch(`/api/orders/${id}/history`);
export const deliverOrder = (id) => apiFetch(`/api/orders/${id}/deliver`, { method: "POST" });
export const cancelOrder = (id, reason) =>
  apiFetch(`/api/orders/${id}/cancel`, { method: "POST", body: { reason: reason || null } });

// --- Cocina
export const fetchKitchenBoard = () => apiFetch("/api/kitchen/board");
export const setKitchenItem = (itemId, status) =>
  apiFetch(`/api/kitchen/items/${itemId}/status`, { method: "PUT", params: { status } });
export const startKitchenOrder = (id) => apiFetch(`/api/kitchen/orders/${id}/start`, { method: "POST" });
export const readyKitchenOrder = (id) => apiFetch(`/api/kitchen/orders/${id}/ready`, { method: "POST" });
export const callWaiter = (id) => apiFetch(`/api/kitchen/orders/${id}/call-waiter`, { method: "POST" });

// --- Domicilios
export const fetchDeliveries = () => apiFetch("/api/deliveries");
export const fetchMyDeliveries = () => apiFetch("/api/deliveries/mine");
export const createDelivery = (payload) => apiFetch("/api/deliveries", { method: "POST", body: payload });
export const startPacking = (id) => apiFetch(`/api/deliveries/${id}/packing/start`, { method: "POST" });
export const finishPacking = (id) => apiFetch(`/api/deliveries/${id}/packing/finish`, { method: "POST" });
export const assignDriver = (id, driverId) =>
  apiFetch(`/api/deliveries/${id}/driver`, { method: "PUT", body: { driverId: driverId || null } });
export const dispatchDelivery = (id) => apiFetch(`/api/deliveries/${id}/dispatch`, { method: "POST" });
export const completeDelivery = (id) => apiFetch(`/api/deliveries/${id}/deliver`, { method: "POST" });
export const fetchPlatforms = () => apiFetch("/api/deliveries/platforms");
export const savePlatform = (id, payload) =>
  apiFetch(id ? `/api/admin/delivery/platforms/${id}` : "/api/admin/delivery/platforms", {
    method: id ? "PUT" : "POST",
    body: payload
  });
export const fetchDrivers = () => apiFetch("/api/deliveries/drivers");
export const saveDriver = (id, payload) =>
  apiFetch(id ? `/api/admin/delivery/drivers/${id}` : "/api/admin/delivery/drivers", {
    method: id ? "PUT" : "POST",
    body: payload
  });

// --- Menú
export const fetchCategories = () => apiFetch("/api/menu/categories");
export const saveCategory = (id, payload) =>
  apiFetch(id ? `/api/admin/menu/categories/${id}` : "/api/admin/menu/categories", {
    method: id ? "PUT" : "POST",
    body: payload
  });
export const deleteCategory = (id) => apiFetch(`/api/admin/menu/categories/${id}`, { method: "DELETE" });
export const fetchAdminMenu = () => apiFetch("/api/admin/menu");
export const setMenuItemActive = (id, value) =>
  apiFetch(`/api/admin/menu/${id}/active`, { method: "PUT", params: { value } });
export const setAvailability = (id, available) =>
  apiFetch(`/api/menu/items/${id}/availability`, { method: "PUT", body: { available } });

// --- Mesas (gerente o MANAGE_TABLES)
export const fetchManagedTables = () => apiFetch("/api/tables/manage");
export const saveManagedTable = (id, payload) =>
  apiFetch(id ? `/api/tables/manage/${id}` : "/api/tables/manage", { method: id ? "PUT" : "POST", body: payload });
export const setTableActive = (id, value) =>
  apiFetch(`/api/tables/manage/${id}/active`, { method: "PUT", params: { value } });
export const deleteManagedTable = (id) => apiFetch(`/api/tables/manage/${id}`, { method: "DELETE" });

// --- Métricas
export const fetchMetrics = (period, from, to) =>
  apiFetch("/api/metrics", { params: period === "custom" ? { period, from, to } : { period } });
