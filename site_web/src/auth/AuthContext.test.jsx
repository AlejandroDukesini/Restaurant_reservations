import { act, render, renderHook, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AuthProvider, homePathForRole, useAuth } from "./AuthContext";
import { readSession, writeSession } from "../api/client";
import * as authApi from "../api/authApi";

describe("homePathForRole", () => {
  it.each([
    ["ADMIN", "/admin"],
    ["COOK", "/cocina"],
    ["EMPLOYEE", "/piso"],
    [null, "/piso"]
  ])("%s -> %s", (role, path) => {
    expect(homePathForRole(role)).toBe(path);
  });
});

describe("AuthProvider", () => {
  const wrapper = ({ children }) => <AuthProvider>{children}</AuthProvider>;

  it("recupera la sesion guardada y la confirma con el servidor", async () => {
    writeSession({ token: "t", role: "COOK", restaurantId: 3 });
    vi.spyOn(authApi, "fetchCurrentUser").mockResolvedValue({
      userId: 4,
      email: "chef@example.test",
      role: "COOK",
      restaurantId: 3
    });
    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.status).toBe("checking");
    await waitFor(() => expect(result.current.status).toBe("ready"));
    expect(result.current.role).toBe("COOK");
    expect(result.current.restaurantId).toBe(3);
  });

  it("login guarda en localStorage solo los datos de sesion devueltos por la API", async () => {
    vi.spyOn(authApi, "login").mockResolvedValue({
      token: "jwt",
      type: "Bearer",
      userId: 9,
      email: "admin@example.test",
      role: "ADMIN",
      restaurantId: 1
    });
    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(() => result.current.login("admin@example.test", "secreto"));

    expect(authApi.login).toHaveBeenCalledWith("admin@example.test", "secreto");
    expect(result.current.isAuthenticated).toBe(true);
    expect(readSession()).toEqual({
      token: "jwt",
      userId: 9,
      email: "admin@example.test",
      role: "ADMIN",
      restaurantId: 1,
      name: "admin@example.test"
    });
    // La contrasena nunca se persiste.
    expect(JSON.stringify(readSession())).not.toContain("secreto");
  });

  it("logout borra la sesion", () => {
    writeSession({ token: "t", role: "ADMIN" });
    vi.spyOn(authApi, "fetchCurrentUser").mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(() => useAuth(), { wrapper });

    act(() => result.current.logout());

    expect(result.current.isAuthenticated).toBe(false);
    expect(readSession()).toBeNull();
  });

  it("useAuth fuera del proveedor falla con un mensaje claro", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    function Orphan() {
      useAuth();
      return null;
    }
    expect(() => render(<Orphan />)).toThrow("useAuth debe usarse dentro de <AuthProvider>");
    expect(screen.queryByText(/./)).toBeNull();
  });
});
