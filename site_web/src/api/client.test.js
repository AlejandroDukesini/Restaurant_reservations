import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch, clearSession, readSession, writeSession } from "./client";

// Respuesta minima compatible con lo que usa apiFetch.
function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => (body === undefined ? Promise.reject(new Error("sin cuerpo")) : Promise.resolve(body))
  };
}

describe("sesion en localStorage", () => {
  it("guarda, lee y borra la sesion", () => {
    writeSession({ token: "t1", role: "COOK" });
    expect(readSession()).toEqual({ token: "t1", role: "COOK" });
    clearSession();
    expect(readSession()).toBeNull();
  });

  it("devuelve null si el contenido guardado no es JSON valido", () => {
    localStorage.setItem("mn.auth", "{roto");
    expect(readSession()).toBeNull();
  });
});

describe("apiFetch", () => {
  let fetchMock;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    window.history.pushState({}, "", "/piso");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("adjunta el JWT de la sesion y serializa el cuerpo como JSON", async () => {
    writeSession({ token: "jwt-123" });
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 1 }));

    const data = await apiFetch("/api/employee/orders", { method: "POST", body: { tableId: 3 } });

    expect(data).toEqual({ id: 1 });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/employee/orders");
    expect(init.method).toBe("POST");
    expect(init.headers).toEqual({ Authorization: "Bearer jwt-123", "Content-Type": "application/json" });
    expect(init.body).toBe(JSON.stringify({ tableId: 3 }));
  });

  it("no envia Authorization en peticiones publicas ni Content-Type sin cuerpo", async () => {
    writeSession({ token: "jwt-123" });
    fetchMock.mockResolvedValue(jsonResponse(200, []));

    await apiFetch("/api/public/restaurants", { auth: false });

    expect(fetchMock.mock.calls[0][1].headers).toEqual({});
    expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
  });

  it("codifica los parametros de consulta", async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));

    await apiFetch("/api/cook/order-items/5/status", { method: "PUT", params: { status: "READY" } });
    await apiFetch("/api/x?a=1", { params: { b: "dos palabras" } });

    expect(fetchMock.mock.calls[0][0]).toBe("/api/cook/order-items/5/status?status=READY");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/x?a=1&b=dos+palabras");
  });

  it("un 401 autenticado cierra la sesion y redirige al login", async () => {
    writeSession({ token: "caducado" });
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, pathname: "/piso", assign });
    fetchMock.mockResolvedValue(jsonResponse(401, {}));

    await expect(apiFetch("/api/staff/tables")).rejects.toThrow("Sesión expirada");
    expect(readSession()).toBeNull();
    expect(assign).toHaveBeenCalledWith("/login");
  });

  it("un 401 en el login (auth: false) muestra el mensaje del servidor y conserva el estado", async () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, pathname: "/login", assign });
    fetchMock.mockResolvedValue(jsonResponse(401, { message: "Invalid credentials" }));

    await expect(apiFetch("/api/auth/login", { method: "POST", auth: false, body: {} })).rejects.toThrow(
      "Invalid credentials"
    );
    expect(assign).not.toHaveBeenCalled();
  });

  it("propaga el mensaje de error de la API o un mensaje generico con el codigo", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(409, { message: "Table already reserved for this time slot" }));
    fetchMock.mockResolvedValueOnce(jsonResponse(500, undefined));

    await expect(apiFetch("/api/public/reservations")).rejects.toThrow("Table already reserved for this time slot");
    await expect(apiFetch("/api/public/reservations")).rejects.toThrow("Error 500");
  });

  it("un 204 devuelve null sin intentar leer el cuerpo", async () => {
    fetchMock.mockResolvedValue(jsonResponse(204, undefined));
    await expect(apiFetch("/api/admin/staff/1", { method: "DELETE" })).resolves.toBeNull();
  });
});
