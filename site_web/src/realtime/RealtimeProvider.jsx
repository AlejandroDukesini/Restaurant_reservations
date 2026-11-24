import { createContext, useContext, useEffect, useMemo, useRef, useState } from "react";
import { readSession } from "../api/client";
import { useAuth } from "../auth/AuthContext";

/*
 * Sincronización entre dispositivos.
 *
 * El servidor emite SEÑALES por Server-Sent Events ("cambiaron los pedidos", "mensaje en el
 * grupo 4"). Al recibirlas, cada vista vuelve a pedir su estado a la API: la base de datos es
 * siempre la fuente de verdad, nunca el almacenamiento local.
 *
 * Se usa fetch (y no EventSource) para enviar el token en la cabecera Authorization en lugar
 * de ponerlo en la URL. Si la conexión cae se reintenta con espera creciente; mientras tanto
 * las vistas se refrescan cada 30 s (polling de respaldo). Al reconectar se emite "resync" y
 * todas las vistas recargan, así un evento perdido durante el corte no deja datos viejos.
 */

const RealtimeContext = createContext({ status: "offline", subscribe: () => () => {} });

const FALLBACK_POLL_MS = 30000;

function deviceKey() {
  try {
    let key = localStorage.getItem("mn.device");
    if (!key) {
      key = (crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`).slice(0, 64);
      localStorage.setItem("mn.device", key);
    }
    return key;
  } catch {
    return "sin-almacenamiento";
  }
}

export function RealtimeProvider({ children, disabled = import.meta.env.MODE === "test" }) {
  const { isAuthenticated, status: authStatus } = useAuth();
  const [status, setStatus] = useState("offline");
  const listeners = useRef(new Set());

  const emit = (event) => {
    for (const listener of listeners.current) {
      try {
        listener(event);
      } catch {
        // Un suscriptor con error no corta la entrega a los demás.
      }
    }
  };

  useEffect(() => {
    if (disabled || !isAuthenticated || authStatus !== "ready") {
      setStatus("offline");
      return undefined;
    }
    let cancelled = false;
    let controller = null;
    let attempt = 0;
    let retryTimer = null;

    const connect = async () => {
      if (cancelled) return;
      const token = readSession()?.token;
      if (!token) return;
      controller = new AbortController();
      setStatus("connecting");
      try {
        const response = await fetch(`/api/stream?device=${encodeURIComponent(deviceKey())}`, {
          headers: { Authorization: `Bearer ${token}`, Accept: "text/event-stream" },
          signal: controller.signal,
          cache: "no-store"
        });
        if (!response.ok || !response.body) throw new Error(`stream ${response.status}`);
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          let index;
          while ((index = buffer.indexOf("\n\n")) >= 0) {
            const block = buffer.slice(0, index);
            buffer = buffer.slice(index + 2);
            const data = block
              .split("\n")
              .filter((line) => line.startsWith("data:"))
              .map((line) => line.slice(5).trim())
              .join("");
            if (!data) continue;
            let event;
            try {
              event = JSON.parse(data);
            } catch {
              continue;
            }
            if (event.topic === "hello") {
              attempt = 0;
              setStatus("online");
              emit({ topic: "resync" });
            } else {
              emit(event);
            }
          }
        }
      } catch {
        // Corte de red, servidor reiniciado o token caducado: se reintenta.
      }
      if (cancelled) return;
      setStatus("offline");
      attempt += 1;
      const delay = Math.min(30000, 1000 * 2 ** Math.min(attempt, 5));
      retryTimer = setTimeout(connect, delay);
    };

    const reconnectNow = () => {
      clearTimeout(retryTimer);
      controller?.abort();
    };
    const onVisible = () => {
      if (document.visibilityState === "visible") emit({ topic: "resync" });
    };

    connect();
    window.addEventListener("online", reconnectNow);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      cancelled = true;
      clearTimeout(retryTimer);
      controller?.abort();
      window.removeEventListener("online", reconnectNow);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [disabled, isAuthenticated, authStatus]);

  // Respaldo: sin canal activo, las vistas se refrescan periódicamente.
  useEffect(() => {
    if (status === "online" || !isAuthenticated) return undefined;
    const timer = setInterval(() => emit({ topic: "resync" }), FALLBACK_POLL_MS);
    return () => clearInterval(timer);
  }, [status, isAuthenticated]);

  const value = useMemo(
    () => ({
      status,
      subscribe: (listener) => {
        listeners.current.add(listener);
        return () => listeners.current.delete(listener);
      }
    }),
    [status]
  );

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

export function useRealtimeStatus() {
  return useContext(RealtimeContext).status;
}

/** Ejecuta `handler` cuando llega una señal de alguno de `topics` (o un "resync"). */
export function useRealtime(topics, handler) {
  const { subscribe } = useContext(RealtimeContext);
  const handlerRef = useRef(handler);
  handlerRef.current = handler;
  const key = topics.join(",");
  useEffect(() => {
    const wanted = new Set(key.split(","));
    return subscribe((event) => {
      if (event.topic === "resync" || wanted.has(event.topic)) handlerRef.current(event);
    });
  }, [subscribe, key]);
}
