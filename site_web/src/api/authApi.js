import { apiFetch } from "./client";

export async function login(email, password) {
  return apiFetch("/api/auth/login", {
    method: "POST",
    auth: false,
    body: { email, password }
  });
}
