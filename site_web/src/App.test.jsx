import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import ProtectedRoute from "./components/ProtectedRoute";
import { AuthProvider } from "./auth/AuthContext";
import { writeSession } from "./api/client";

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
  ])("%s en %s ve %s", (role, path, heading) => {
    writeSession({ token: "t", role });
    renderAt(path);
    expect(screen.getByRole("heading", { name: heading })).toBeInTheDocument();
  });

  it.each([
    ["COOK", "/admin", "Pagina Cocina"],
    ["COOK", "/piso", "Pagina Cocina"],
    ["EMPLOYEE", "/admin", "Pagina Piso"],
    ["EMPLOYEE", "/cocina", "Pagina Piso"]
  ])("%s sin permiso para %s vuelve a su panel (%s)", (role, path, heading) => {
    writeSession({ token: "t", role });
    renderAt(path);
    expect(screen.getByRole("heading", { name: heading })).toBeInTheDocument();
  });

  it("una ruta desconocida redirige segun la sesion", () => {
    writeSession({ token: "t", role: "COOK" });
    renderAt("/no-existe");
    expect(screen.getByRole("heading", { name: "Pagina Cocina" })).toBeInTheDocument();
  });
});

describe("ProtectedRoute", () => {
  it("renderiza el contenido cuando el rol esta permitido", () => {
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
    expect(screen.getByText("contenido privado")).toBeInTheDocument();
  });
});
