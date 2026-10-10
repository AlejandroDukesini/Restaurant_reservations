import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProtectedRoute from "./components/ProtectedRoute";
import { AuthProvider } from "./auth/AuthContext";
import { readSession, writeSession } from "./api/client";
import * as authApi from "./api/authApi";

// Las paginas reales hacen peticiones al montar; aqui solo interesa el enrutado por rol.
vi.mock("./pages/FloorPage", () => ({ default: () => <h1>Pagina Piso</h1> }));
vi.mock("./pages/KitchenPage", () => ({ default: () => <h1>Pagina Cocina</h1> }));
vi.mock("./pages/AdminPage", () => ({ default: () => <h1>Pagina Admin</h1> }));
vi.mock("./pages/LoginPage", () => ({ default: () => <h1>Pagina Login</h1> }));

const { default: App } = await import("./App");

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>
  );
}

// Por defecto el servidor confirma la sesion guardada con el mismo rol.
beforeEach(() => {
  vi.spyOn(authApi, "fetchCurrentUser").mockImplementation(async () => ({ ...readSession() }));
});

describe("enrutado por rol", () => {
  it("sin sesion cualquier ruta privada lleva al login", () => {
    renderAt("/admin");
    expect(screen.getByRole("heading", { name: "Pagina Login" })).toBeInTheDocument();
  });

  it.each([
    ["ADMIN", "/", "Pagina Admin"],
    ["COOK", "/", "Pagina Cocina"],
    ["EMPLOYEE", "/", "Pagina Piso"],
    ["ADMIN", "/cocina", "Pagina Cocina"],
    ["ADMIN", "/piso", "Pagina Piso"]
  ])("%s en %s ve %s", async (role, path, heading) => {
    writeSession({ token: "t", role });
    renderAt(path);
    expect(await screen.findByRole("heading", { name: heading })).toBeInTheDocument();
  });

  it.each([
    ["COOK", "/admin", "Pagina Cocina"],
    ["COOK", "/piso", "Pagina Cocina"],
    ["EMPLOYEE", "/admin", "Pagina Piso"],
    ["EMPLOYEE", "/cocina", "Pagina Piso"]
  ])("%s sin permiso para %s vuelve a su panel (%s)", async (role, path, heading) => {
    writeSession({ token: "t", role });
    renderAt(path);
    expect(await screen.findByRole("heading", { name: heading })).toBeInTheDocument();
  });

  it("una ruta desconocida redirige segun la sesion", async () => {
    writeSession({ token: "t", role: "COOK" });
    renderAt("/no-existe");
    expect(await screen.findByRole("heading", { name: "Pagina Cocina" })).toBeInTheDocument();
  });
});

describe("verificacion de la sesion guardada", () => {
  it("no muestra contenido restringido hasta que el servidor confirma el rol", async () => {
    writeSession({ token: "t", role: "ADMIN" });
    // El rol cambio en el servidor: manda el rol confirmado, no el guardado.
    authApi.fetchCurrentUser.mockResolvedValue({ userId: 1, email: "x@example.test", role: "COOK" });
    renderAt("/admin");

    expect(screen.getByRole("status")).toHaveTextContent("Verificando tu sesión...");
    expect(screen.queryByRole("heading", { name: "Pagina Admin" })).toBeNull();
    expect(await screen.findByRole("heading", { name: "Pagina Cocina" })).toBeInTheDocument();
    expect(readSession().role).toBe("COOK");
  });

  it("si el token ya no es valido vuelve al login", async () => {
    writeSession({ token: "caducado", role: "ADMIN" });
    authApi.fetchCurrentUser.mockImplementation(async () => {
      localStorage.clear(); // lo que hace apiFetch ante un 401
      throw new Error("Sesión expirada. Inicia sesión de nuevo.");
    });
    renderAt("/admin");

    expect(await screen.findByRole("heading", { name: "Pagina Login" })).toBeInTheDocument();
  });

  it("ante un fallo de red conserva la sesion y permite reintentar", async () => {
    writeSession({ token: "t", role: "ADMIN" });
    authApi.fetchCurrentUser
      .mockRejectedValueOnce(new Error("No se pudo conectar con el servidor."))
      .mockResolvedValueOnce({ userId: 1, email: "x@example.test", role: "ADMIN" });
    renderAt("/admin");

    expect(await screen.findByText("No se pudo conectar con el servidor.")).toBeInTheDocument();
    expect(readSession()).not.toBeNull();
    await userEvent.setup().click(screen.getByRole("button", { name: "Reintentar" }));
    expect(await screen.findByRole("heading", { name: "Pagina Admin" })).toBeInTheDocument();
  });
});

describe("ProtectedRoute", () => {
  it("renderiza el contenido cuando el rol esta permitido", async () => {
    writeSession({ token: "t", role: "ADMIN" });
    render(
      <MemoryRouter initialEntries={["/x"]}>
        <AuthProvider>
          <Routes>
            <Route
              path="/x"
              element={
                <ProtectedRoute roles={["ADMIN"]}>
                  <p>contenido privado</p>
                </ProtectedRoute>
              }
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    );
    expect(await screen.findByText("contenido privado")).toBeInTheDocument();
  });
});
