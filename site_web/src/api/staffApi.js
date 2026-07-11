import { apiFetch } from "./client";

export const fetchStaff = () => apiFetch("/api/admin/staff");

export const createStaff = (payload) =>
  apiFetch("/api/admin/staff", { method: "POST", body: payload });

export const updateStaff = (id, payload) =>
  apiFetch(`/api/admin/staff/${id}`, { method: "PUT", body: payload });

export const deleteStaff = (id) =>
  apiFetch(`/api/admin/staff/${id}`, { method: "DELETE" });
