import { useCallback, useEffect, useRef, useState } from "react";
import { Bell, BellRing, CheckCheck } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { fetchNotifications, fetchUnread, markNotificationsRead } from "../api/opsApi";
import { useAuth } from "../auth/AuthContext";
import { useRealtime } from "../realtime/RealtimeProvider";
import { askNotifications, canAskNotifications, systemNotify } from "../pwa/pwa";
import { timeOf } from "../utils/status";

/** Ruta de la operación a la que apunta una notificación. */
export function targetFor(notification, role) {
  if (notification.refType === "TABLE") return `/piso?mesa=${notification.refId}`;
  if (notification.refType === "ORDER") {
    if (role === "DELIVERY") return "/entregas";
    if (notification.type.startsWith("DELIVERY")) return "/domicilios";
    if (role === "COOK") return "/cocina";
    return `/piso?pedido=${notification.refId}`;
  }
  return null;
}

export default function NotificationBell() {
  const { role } = useAuth();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState(null);
  const [open, setOpen] = useState(false);
  const lastSeen = useRef(null);
  const panel = useRef(null);

  const refreshCount = useCallback(async () => {
    try {
      const { unread: count } = await fetchUnread();
      setUnread(count);
      return count;
    } catch {
      return null;
    }
  }, []);

  const loadList = useCallback(async () => {
    try {
      const list = await fetchNotifications();
      setItems(list);
      const newest = list[0];
      if (newest && lastSeen.current !== null && newest.id > lastSeen.current && !newest.read) {
        systemNotify(newest.title, newest.body || "");
      }
      if (newest) lastSeen.current = Math.max(lastSeen.current ?? 0, newest.id);
    } catch {
      // Se reintenta con la siguiente señal.
    }
  }, []);

  useEffect(() => {
    refreshCount();
    loadList();
  }, [refreshCount, loadList]);

  useRealtime(["notifications"], () => {
    refreshCount();
    loadList();
  });

  useEffect(() => {
    if (!open) return undefined;
    const close = (event) => {
      if (panel.current && !panel.current.contains(event.target)) setOpen(false);
    };
    const onKey = (event) => event.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const openItem = async (item) => {
    setOpen(false);
    if (!item.read) {
      await markNotificationsRead(item.id).catch(() => {});
      refreshCount();
      loadList();
    }
    const target = targetFor(item, role);
    if (target) navigate(target);
  };

  const readAll = async () => {
    await markNotificationsRead().catch(() => {});
    refreshCount();
    loadList();
  };

  const Icon = unread > 0 ? BellRing : Bell;

  return (
    <div className="relative" ref={panel}>
      <button
        type="button"
        onClick={() => {
          setOpen((v) => !v);
          if (canAskNotifications()) askNotifications();
        }}
        className="relative grid h-10 w-10 place-items-center rounded-full border border-white/10 text-zinc-300 hover:border-gold/50"
        aria-label={`Notificaciones${unread ? `: ${unread} sin leer` : ""}`}
        aria-expanded={open}
      >
        <Icon className="h-5 w-5" />
        {unread > 0 && (
          <span className="absolute -right-1 -top-1 grid h-5 min-w-5 place-items-center rounded-full bg-red-500 px-1 text-[10px] font-bold" style={{ color: "#fff" }}>
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="motion-dialog absolute right-0 z-50 mt-2 w-[min(22rem,calc(100vw-1.5rem))] border border-white/10 bg-graphite shadow-2xl">
          <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
            <p className="text-sm font-semibold text-champagne">Notificaciones</p>
            <button type="button" onClick={readAll} className="inline-flex items-center gap-1 text-xs text-zinc-400 hover:text-champagne">
              <CheckCheck className="h-4 w-4" /> Marcar todas
            </button>
          </div>
          <ul className="max-h-96 overflow-y-auto">
            {!items || items.length === 0 ? (
              <li className="px-4 py-6 text-center text-sm text-zinc-500">Sin notificaciones.</li>
            ) : (
              items.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => openItem(item)}
                    className={`flex w-full flex-col items-start gap-0.5 border-b border-white/5 px-4 py-3 text-left hover:bg-white/[.04] ${item.read ? "opacity-60" : ""}`}
                  >
                    <span className="flex w-full items-center justify-between gap-2">
                      <span className="text-sm text-champagne">{item.title}</span>
                      <span className="shrink-0 text-[11px] text-zinc-500">{timeOf(item.createdAt)}</span>
                    </span>
                    {item.body && <span className="text-xs text-zinc-400">{item.body}</span>}
                  </button>
                </li>
              ))
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
