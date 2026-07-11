import { apiFetch } from "./client";

export const fetchTables = () => apiFetch("/api/staff/tables");

export const createTable = (payload) =>
  apiFetch("/api/employee/tables", { method: "POST", body: payload });

export const updateTable = (id, payload) =>
  apiFetch(`/api/employee/tables/${id}`, { method: "PUT", body: payload });

export const deleteTable = (id) =>
  apiFetch(`/api/employee/tables/${id}`, { method: "DELETE" });
