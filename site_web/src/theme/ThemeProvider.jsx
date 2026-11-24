import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { fetchBranding } from "../api/opsApi";
import { useRealtime } from "../realtime/RealtimeProvider";
import { DEFAULT_THEME, applyTheme, effectiveTheme } from "./theme";

/*
 * Identidad visual efectiva. El servidor decide qué tema corresponde al usuario (el de su
 * grupo o el del restaurante); aquí solo se aplica. La preferencia oscuro/claro es del
 * dispositivo (comodidad visual) y se recuerda en localStorage. Una copia de la última
 * identidad recibida evita el parpadeo al abrir la app; la fuente de verdad es la API.
 */

const ThemeContext = createContext(null);
const PREF_KEY = "mn.themePref";
const CACHE_KEY = "mn.branding";

function readJson(key) {
  try {
    return JSON.parse(localStorage.getItem(key) || "null");
  } catch {
    return null;
  }
}

function readPref() {
  try {
    return localStorage.getItem(PREF_KEY) || "brand";
  } catch {
    return "brand";
  }
}

export function ThemeProvider({ children }) {
  const { isAuthenticated, status } = useAuth();
  const [branding, setBranding] = useState(() => readJson(CACHE_KEY));
  const [pref, setPrefState] = useState(readPref);
  const [preview, setPreview] = useState(null);

  const refresh = useCallback(async () => {
    if (!isAuthenticated || status !== "ready") return;
    try {
      const data = await fetchBranding();
      setBranding(data);
      try {
        localStorage.setItem(CACHE_KEY, JSON.stringify(data));
      } catch {
        // Sin almacenamiento: solo se pierde la copia para el arranque.
      }
    } catch {
      // Se conserva la identidad anterior; el siguiente "resync" lo reintenta.
    }
  }, [isAuthenticated, status]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    if (!isAuthenticated) setPreview(null);
  }, [isAuthenticated]);

  useRealtime(["branding", "groups"], refresh);

  const theme = useMemo(
    () => preview || effectiveTheme(branding?.theme || DEFAULT_THEME, pref),
    [branding, pref, preview]
  );

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  const setPref = useCallback((value) => {
    setPrefState(value);
    try {
      localStorage.setItem(PREF_KEY, value);
    } catch {
      // Preferencia solo para esta sesión.
    }
  }, []);

  const value = useMemo(
    () => ({
      branding,
      theme,
      pref,
      setPref,
      refresh,
      setPreview,
      restaurantName: branding?.restaurantName || "Maison Noir",
      logoUrl: branding?.logoUrl || null
    }),
    [branding, theme, pref, setPref, refresh]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  return (
    useContext(ThemeContext) || {
      branding: null,
      theme: DEFAULT_THEME,
      pref: "brand",
      setPref: () => {},
      refresh: () => {},
      setPreview: () => {},
      restaurantName: "Maison Noir",
      logoUrl: null
    }
  );
}
