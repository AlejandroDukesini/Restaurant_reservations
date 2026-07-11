import { createContext, useCallback, useContext, useMemo, useState } from "react";
import { login as loginRequest } from "../api/authApi";
import { clearSession, readSession, writeSession } from "../api/client";

const AuthContext = createContext(null);

// Ruta de inicio según el rol del usuario autenticado.
export function homePathForRole(role) {
  switch (role) {
    case "ADMIN":
      return "/admin";
    case "COOK":
      return "/cocina";
    case "EMPLOYEE":
    default:
      return "/piso";
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => readSession());

  const login = useCallback(async (email, password) => {
    const data = await loginRequest(email, password);
    const next = {
      token: data.token,
      userId: data.userId,
      email: data.email,
      role: data.role,
      restaurantId: data.restaurantId,
      name: data.email
    };
    writeSession(next);
    setSession(next);
    return next;
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
  }, []);

  const value = useMemo(
    () => ({
      session,
      isAuthenticated: Boolean(session?.token),
      role: session?.role ?? null,
      restaurantId: session?.restaurantId ?? null,
      login,
      logout
    }),
    [session, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth debe usarse dentro de <AuthProvider>");
  return ctx;
}
