// Prueba de carga k6 para el endpoint más pesado del sistema: la cola de cocina.
// GET /api/cook/queue agrega, por restaurante, todos los platos aún no listos de los
// pedidos activos junto con su receta, mesa y mesero. Es el endpoint que más relaciones
// atraviesa, por lo que es el mejor candidato para medir el impacto de eliminar el N+1.
//
// Uso (backend en http://localhost:8081, base de datos sembrada):
//   k6 run perf/cook-queue-load.js
//   BASE_URL=http://localhost:8081 k6 run perf/cook-queue-load.js
//
// Autentica una vez (setup) y reutiliza el JWT en todas las VUs.

import http from "k6/http";
import { check } from "k6";
import { Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8081";
const EMAIL = __ENV.EMAIL || "cocina1@maisonnoir.com";
const PASSWORD = __ENV.PASSWORD || "password123";

const errorRate = new Rate("errores");
const queueLatency = new Trend("cola_ms", true);

export const options = {
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
  scenarios: {
    carga: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "10s", target: 20 }, // rampa de subida
        { duration: "30s", target: 20 }, // meseta sostenida
        { duration: "5s", target: 0 },   // bajada
      ],
      gracefulRampDown: "5s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"], // < 1% de fallos
    http_req_duration: ["p(95)<500", "p(99)<1000"],
  },
};

export function setup() {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" } }
  );
  check(res, { "login 200": (r) => r.status === 200 });
  return { token: res.json("token") };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/api/cook/queue`, {
    headers: { Authorization: `Bearer ${data.token}` },
  });
  const ok = check(res, { "queue 200": (r) => r.status === 200 });
  errorRate.add(!ok);
  queueLatency.add(res.timings.duration);
}
