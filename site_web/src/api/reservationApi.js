import { apiFetch } from "./client";

// Reservas del restaurante del administrador (el servidor filtra por restaurante).
export const fetchRestaurantReservations = () => apiFetch("/api/admin/reservations");

export const confirmReservation = (id) =>
  apiFetch(`/api/customer/reservations/${id}/confirm`, { method: "PUT" });

export const cancelReservation = (id) =>
  apiFetch(`/api/customer/reservations/${id}/cancel`, { method: "PUT" });

// Reserva a nombre de un cliente (p. ej. por teléfono). Es el mismo endpoint del sitio
// público: el servidor valida aforo y solapamiento y la deja confirmada.
export const createReservation = (payload) =>
  apiFetch("/api/public/reservations", { method: "POST", body: payload });
