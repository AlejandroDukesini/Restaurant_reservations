/*
 * Service worker de la PWA.
 *
 * Qué funciona sin conexión:
 *   - Abrir la app (carcasa: HTML, JS, CSS, iconos) y ver las imágenes ya vistas.
 * Qué NO se cachea nunca (requiere conexión):
 *   - /api/** (pedidos, mesas, cocina, mensajes, métricas, sesión). La base de datos es la
 *     única fuente de verdad; mostrar datos operativos viejos como actuales sería peligroso.
 *   - Excepción: /api/media/* (imágenes inmutables con id aleatorio), cache-first.
 *
 * Actualización: una versión nueva espera hasta que la app envía SKIP_WAITING (botón
 * "Actualizar"), para no recargar en medio de una operación.
 */
const VERSION = "v2";
const SHELL = `shell-${VERSION}`;
const ASSETS = `assets-${VERSION}`;
const MEDIA = "media-v1";
const SHELL_URLS = ["/", "/manifest.webmanifest", "/favicon.svg", "/icons/icon-192.png", "/icons/icon-512.png"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(SHELL).then((cache) => cache.addAll(SHELL_URLS)));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => ![SHELL, ASSETS, MEDIA].includes(k)).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("message", (event) => {
  if (event.data?.type === "SKIP_WAITING") self.skipWaiting();
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (url.pathname.startsWith("/api/media/")) {
    event.respondWith(cacheFirst(request, MEDIA));
    return;
  }
  if (url.pathname.startsWith("/api/")) return; // siempre a la red

  if (request.mode === "navigate") {
    // Red primero; sin conexión, la carcasa en caché (la app avisa que está sin conexión).
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone();
          caches.open(SHELL).then((cache) => cache.put("/", copy));
          return response;
        })
        .catch(() => caches.match("/"))
    );
    return;
  }

  if (url.pathname.startsWith("/assets/") || url.pathname.startsWith("/icons/")) {
    event.respondWith(cacheFirst(request, ASSETS));
  }
});

async function cacheFirst(request, cacheName) {
  const cached = await caches.match(request);
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok) {
    const cache = await caches.open(cacheName);
    cache.put(request, response.clone());
  }
  return response;
}

// Clic en una notificación del sistema: enfoca la app abierta o la abre.
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((list) => {
      const client = list.find((c) => "focus" in c);
      return client ? client.focus() : self.clients.openWindow("/");
    })
  );
});
