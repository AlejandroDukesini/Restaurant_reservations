import { apiFetch } from "./client";

export const fetchMenu = () => apiFetch("/api/staff/menu");

export const createMenuItem = (payload) =>
  apiFetch("/api/admin/menu", { method: "POST", body: payload });

export const updateMenuItem = (id, payload) =>
  apiFetch(`/api/admin/menu/${id}`, { method: "PUT", body: payload });

export const deleteMenuItem = (id) =>
  apiFetch(`/api/admin/menu/${id}`, { method: "DELETE" });
