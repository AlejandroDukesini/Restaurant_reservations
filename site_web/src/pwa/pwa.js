import { useEffect, useState } from "react";

/*
 * Instalación y actualización de la PWA.
 *
 * - El service worker (public/sw.js) solo cachea la "carcasa" de la app y las imágenes; la
 *   API nunca se cachea: pedidos, mesas y mensajes requieren conexión y vienen del servidor.
 * - Una versión nueva queda "en espera" hasta que el usuario pulsa Actualizar, para no
 *   recargar en medio de una operación.
 */

let deferredPrompt = null;
const installListeners = new Set();
const updateListeners = new Set();
let waitingWorker = null;

function notify(set, value) {
  set.forEach((fn) => fn(value));
}

export function registerServiceWorker() {
  if (!("serviceWorker" in navigator) || import.meta.env.DEV) return;
  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    deferredPrompt = event;
    notify(installListeners, true);
  });
  window.addEventListener("appinstalled", () => {
    deferredPrompt = null;
    notify(installListeners, false);
  });
  window.addEventListener("load", async () => {
    try {
      const registration = await navigator.serviceWorker.register("/sw.js");
      const track = (worker) => {
        worker?.addEventListener("statechange", () => {
          if (worker.state === "installed" && navigator.serviceWorker.controller) {
            waitingWorker = worker;
            notify(updateListeners, true);
          }
        });
      };
      if (registration.waiting && navigator.serviceWorker.controller) {
        waitingWorker = registration.waiting;
        notify(updateListeners, true);
      }
      registration.addEventListener("updatefound", () => track(registration.installing));
      // Comprueba si hay versión nueva cada 30 minutos.
      setInterval(() => registration.update().catch(() => {}), 30 * 60 * 1000);
    } catch {
      // Sin service worker la app sigue funcionando en línea.
    }
  });
  let reloading = false;
  navigator.serviceWorker.addEventListener("controllerchange", () => {
    if (reloading) return;
    reloading = true;
    window.location.reload();
  });
}

export function useInstallPrompt() {
  const [available, setAvailable] = useState(Boolean(deferredPrompt));
  useEffect(() => {
    installListeners.add(setAvailable);
    return () => installListeners.delete(setAvailable);
  }, []);
  const install = async () => {
    if (!deferredPrompt) return;
    deferredPrompt.prompt();
    await deferredPrompt.userChoice.catch(() => null);
    deferredPrompt = null;
    setAvailable(false);
  };
  return { available, install };
}

export function useUpdateAvailable() {
  const [available, setAvailable] = useState(Boolean(waitingWorker));
  useEffect(() => {
    updateListeners.add(setAvailable);
    return () => updateListeners.delete(setAvailable);
  }, []);
  const apply = () => waitingWorker?.postMessage({ type: "SKIP_WAITING" });
  return { available, apply };
}

export function useOnline() {
  const [online, setOnline] = useState(typeof navigator === "undefined" ? true : navigator.onLine);
  useEffect(() => {
    const on = () => setOnline(true);
    const off = () => setOnline(false);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
    };
  }, []);
  return online;
}

/** Aviso del sistema operativo (Android/Windows) si la app está en segundo plano. */
export async function systemNotify(title, body) {
  if (typeof Notification === "undefined" || Notification.permission !== "granted") return;
  if (document.visibilityState === "visible") return;
  try {
    const registration = await navigator.serviceWorker?.getRegistration();
    if (registration) {
      await registration.showNotification(title, { body, icon: "/icons/icon-192.png", badge: "/icons/icon-192.png" });
    } else {
      new Notification(title, { body });
    }
  } catch {
    // Notificación del sistema no disponible: queda la de la app.
  }
}

export function canAskNotifications() {
  return typeof Notification !== "undefined" && Notification.permission === "default";
}

export function askNotifications() {
  if (typeof Notification === "undefined") return Promise.resolve("denied");
  return Notification.requestPermission();
}
