import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { fetchCurrentUser, login as loginRequest } from "../api/authApi";
import { clearSession, readSession, writeSession } from "../api/client";

const AuthContext = createContext(null);

// Ruta de inicio según el rol del usuario autenticado.
export function homePathForRole(role) {
  switch (role) {
    case "ADMIN":
      return "/admin";
    case "COOK":
      return "/cocina";
    case "DELIVERY":
      return "/entregas";
    case "EMPLOYEE":
    default:
      return "/piso";
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => readSession());
  // Una sesion guardada se confirma con el servidor antes de mostrar vistas protegidas:
  // el token puede haber caducado o el rol/usuario haber cambiado desde el ultimo uso.
  const [status, setStatus] = useState(() => (readSession()?.token ? "checking" : "ready"));
  const [sessionError, setSessionError] = useState("");

  const verifySession = useCallback(async () => {
    setStatus("checking");
    setSessionError("");
    try {
      const user = await fetchCurrentUser();
      const stored = readSession();
      if (stored) {
        const next = {
          ...stored,
          userId: user.userId,
          email: user.email,
          role: user.role,
          restaurantId: user.restaurantId,
          name: user.name || stored.name,
          permissions: user.permissions || [],
          avatarUrl: user.avatarUrl || null
        };
        writeSession(next);
        setSession(next);
      }
      setStatus("ready");
    } catch (err) {
      // Un 401 ya borro la sesion en apiFetch: se vuelve al login. Un fallo de red
      // conserva la sesion para poder reintentar.
      if (!readSession()) {
        setSession(null);
        setStatus("ready");
      } else {
        setSessionError(err.message);
        setStatus("error");
      }
    }
  }, []);

  useEffect(() => {
    if (readSession()?.token) verifySession();
  }, [verifySession]);

  const login = useCallback(async (email, password) => {
    const data = await loginRequest(email, password);
    const next = {
      token: data.token,
      userId: data.userId,
      email: data.email,
      role: data.role,
      restaurantId: data.restaurantId,
      name: data.email,
      permissions: []
    };
    writeSession(next);
    setSession(next);
    setStatus("ready");
    // Nombre y permisos vigentes salen de /me (no del token); en segundo plano.
    fetchCurrentUser()
      .then((user) => {
        const stored = readSession();
        if (!stored || stored.userId !== user.userId) return;
        const merged = {
          ...stored,
          name: user.name || stored.name,
          permissions: user.permissions || [],
          avatarUrl: user.avatarUrl || null
        };
        writeSession(merged);
        setSession(merged);
      })
      .catch(() => {});
    return next;
  }, []);

  /** Refleja en la sesión el nombre y la foto recién guardados en el perfil. */
  const updateProfile = useCallback((changes) => {
    const stored = readSession();
    if (!stored) return;
    const next = { ...stored, ...changes };
    writeSession(next);
    setSession(next);
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
    setStatus("ready");
  }, []);

  const value = useMemo(
    () => ({
      session,
      isAuthenticated: Boolean(session?.token),
      role: session?.role ?? null,
      userId: session?.userId ?? null,
      restaurantId: session?.restaurantId ?? null,
      permissions: session?.permissions ?? [],
      // Solo decide qué se muestra; el servidor vuelve a autorizar cada operación.
      can: (permission) => session?.role === "ADMIN" || (session?.permissions || []).includes(permission),
      status,
      sessionError,
      retrySession: verifySession,
      login,
      logout,
      updateProfile
    }),
    [session, status, sessionError, verifySession, login, logout, updateProfile]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth debe usarse dentro de <AuthProvider>");
  return ctx;
}
