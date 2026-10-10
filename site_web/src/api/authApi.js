import { apiFetch } from "./client";

export async function login(email, password) {
  return apiFetch("/api/auth/login", {
    method: "POST",
    auth: false,
    body: { email, password }
  });
}

// Confirma con el servidor que el token guardado sigue vigente y devuelve el rol actual.
export async function fetchCurrentUser() {
  return apiFetch("/api/auth/me", { redirectOnUnauthorized: false });
}
