// Cliente fetch centralizado: inyecta el token JWT y maneja el 401.
const STORAGE_KEY = "mn.auth";
// Sin respuesta en este tiempo la petición se aborta: ninguna vista queda cargando sin fin.
const REQUEST_TIMEOUT_MS = 15000;

export function readSession() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function writeSession(session) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearSession() {
  localStorage.removeItem(STORAGE_KEY);
}

export async function apiFetch(
  path,
  { method = "GET", body, auth = true, params, redirectOnUnauthorized = true } = {}
) {
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";

  if (auth) {
    const session = readSession();
    if (session?.token) headers["Authorization"] = `Bearer ${session.token}`;
  }

  let url = path;
  if (params) {
    const qs = new URLSearchParams(params).toString();
    url += (url.includes("?") ? "&" : "?") + qs;
  }

  let response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS)
    });
  } catch (err) {
    throw new Error(
      err?.name === "TimeoutError"
        ? "El servidor tardó demasiado en responder. Inténtalo de nuevo."
        : "No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo."
    );
  }

  // Solo un 401 en una petición autenticada significa "sesión caducada".
  // En el login (auth: false) el 401 es "credenciales inválidas" y debe llegar
  // al formulario con su propio mensaje.
  if (response.status === 401 && auth) {
    clearSession();
    if (redirectOnUnauthorized && !window.location.pathname.startsWith("/login")) {
      window.location.assign("/login");
    }
    throw new Error("Sesión expirada. Inicia sesión de nuevo.");
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || `Error ${response.status}`);
  }

  if (response.status === 204) return null;
  return response.json();
}
