import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import LoginPage from "./LoginPage";
import { AuthProvider } from "../auth/AuthContext";
import { readSession, writeSession } from "../api/client";

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/admin" element={<h1>Panel admin</h1>} />
          <Route path="/cocina" element={<h1>Panel cocina</h1>} />
          <Route path="/piso" element={<h1>Panel piso</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  );
}

function mockFetch(status, body) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body)
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("LoginPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("inicia sesion y lleva al panel del rol devuelto por la API", async () => {
    const fetchMock = mockFetch(200, { token: "jwt", userId: 1, email: "chef@example.test", role: "COOK", restaurantId: 1 });
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText("Correo"), "chef@example.test");
    await user.type(screen.getByLabelText("Contraseña"), "una-clave-larga");
    await user.click(screen.getByRole("button", { name: "Ingresar" }));

    expect(await screen.findByRole("heading", { name: "Panel cocina" })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/auth/login",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ email: "chef@example.test", password: "una-clave-larga" })
      })
    );
    expect(readSession().role).toBe("COOK");
  });

  it("muestra el error de credenciales y permanece en el login", async () => {
    mockFetch(401, { message: "Invalid credentials" });
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText("Correo"), "chef@example.test");
    await user.type(screen.getByLabelText("Contraseña"), "incorrecta");
    await user.click(screen.getByRole("button", { name: "Ingresar" }));

    expect(await screen.findByText("Invalid credentials")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Ingresar" })).toBeEnabled();
    expect(readSession()).toBeNull();
  });

  it("los botones de demo rellenan el formulario sin enviarlo", async () => {
    const fetchMock = mockFetch(200, {});
    const user = userEvent.setup();
    renderLogin();

    await user.click(screen.getByRole("button", { name: "Mesero" }));

    expect(screen.getByLabelText("Correo")).toHaveValue("mesero1@maisonnoir.com");
    expect(screen.getByLabelText("Contraseña")).toHaveValue("password123");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("con sesion activa redirige al panel sin avisos de React por navegar durante el render", async () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    writeSession({ token: "t", role: "ADMIN" });

    renderLogin();

    expect(await screen.findByRole("heading", { name: "Panel admin" })).toBeInTheDocument();
    expect(consoleError).not.toHaveBeenCalled();
  });
});
