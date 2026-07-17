# Metodología de medición de rendimiento

Todas las cifras de la sección **Performance & Scalability** del README se midieron de forma
reproducible en este proyecto. No son estimaciones: aquí están los comandos exactos para
regenerarlas.

## Entorno de medición

- **Máquina:** laptop de desarrollo (Windows 11), PostgreSQL 18 local.
- **Backend:** Kotlin + Spring Boot 3.2, JDK 21, pool de conexiones HikariCP (por defecto en Spring Boot).
- **Frontend:** build de producción de Vite (`site_web/dist`), servido con `vite preview`.
- **Dataset de carga:** 150 pedidos activos × 3 platos = **450 ítems en la cola de cocina**
  (ver "Sembrado de datos").

> Las latencias absolutas dependen del hardware; lo relevante y reproducible es la **relación
> antes/después** de cada optimización.

---

## 1. Backend — eliminación del problema N+1 (k6)

El endpoint más pesado es `GET /api/cook/queue` (`CookService.getQueue`): agrega, por
restaurante, todos los platos no listos de los pedidos activos junto con su receta, mesa y mesero.

**Optimización:** `OrderRepository.findActiveByRestaurant` pasó de traer solo los `Order`
(disparando carga *lazy* de `items`, `table.restaurant`, `employee` y `menuItem` de cada plato)
a resolver todo en una sola consulta con `LEFT JOIN FETCH` / `JOIN FETCH`.

### Conteo de consultas SQL (determinista)

Con `JPA_SHOW_SQL=true`, se cuenta cuántas sentencias `Hibernate:` emite **una** petición:

```bash
# 1 request a la cola con 450 ítems
curl -s http://localhost:8081/api/cook/queue -H "Authorization: Bearer <JWT>"
# contar líneas "Hibernate:" que produjo esa petición en el log del backend
```

| Versión                | Consultas SQL por request |
| ---------------------- | ------------------------: |
| Antes (N+1)            |                       157 |
| Después (`JOIN FETCH`) |                         3 |

→ **98.1 % menos consultas** (3 = 1 consulta agregada + resolución de usuario/sesión).

### Carga sostenida (k6)

```bash
k6 run perf/cook-queue-load.js
# rampa a 20 VUs, ~45 s. Reutiliza un JWT en setup().
```

| Métrica          | Antes (N+1) | Después (fix) |  Mejora |
| ---------------- | ----------: | ------------: | ------: |
| Throughput (RPS) |        2.82 |          ~52  | 18.4× ↑ |
| Latencia media   |     5.53 s  |        309 ms | 94.4% ↓ |
| Latencia p95     |     8.77 s  |        767 ms | 91.3% ↓ |
| Latencia p99     |     9.13 s  |       1.24 s  | 86.4% ↓ |
| Tasa de éxito    |       100%  |         100%  |       — |

Las cifras de "después" son la mediana de 3 corridas en estado estable (tras calentamiento JIT).
La primera corrida en frío es descartada por warmup de la JVM.

---

## 2. Frontend — Lighthouse

```bash
cd site_web
corepack pnpm build
corepack pnpm preview            # sirve dist/ en http://127.0.0.1:4173
# en otra terminal:
npx lighthouse http://127.0.0.1:4173/ \
  --only-categories=performance,accessibility,best-practices,seo \
  --form-factor=mobile --screenEmulation.mobile      # móvil
npx lighthouse http://127.0.0.1:4173/ --preset=desktop  # escritorio
```

| Categoría      | 📱 Móvil | 🖥️ Escritorio |
| -------------- | :------: | :------------: |
| Performance    |   97–99  |      100       |
| Accessibility  |    100   |      100       |
| Best Practices |    100   |      100       |
| SEO            |    100   |      100       |

**Core Web Vitals:** LCP 1.7 s móvil / 0.4 s escritorio · TBT ≤ 140 ms · CLS 0.

Optimizaciones aplicadas para llegar a estos puntajes:

- **`text-zinc-400`** en el bloque de credenciales del login → contraste ≥ 4.5:1 (a11y 100).
- **favicon SVG** en `public/` → sin 404 en consola (best-practices 100).
- **`<meta name="description">` + `robots.txt`** → SEO 100.
- Rendimiento base de Vite: tree-shaking, minificación, code-splitting y CSS purgado por Tailwind;
  bundle único y liviano, CLS 0 (sin saltos de layout).

---

## Sembrado de datos (para reproducir la carga del backend)

```sql
-- 150 pedidos activos × 3 platos, marcados con notes='PERFSEED'
INSERT INTO orders (order_date, status, total_amount, employee_id, table_id, notes)
SELECT now() - (g || ' minutes')::interval, 0, 0.0, 6 + (g % 2), 1 + (g % 3), 'PERFSEED'
FROM generate_series(1,150) g;

INSERT INTO order_items (item_name, price, quantity, status, order_id, menu_item_id)
SELECT mi.name, mi.price, 1, 'PENDING', o.id, mi.id
FROM orders o
JOIN LATERAL (
  SELECT id, name, price FROM menu_items WHERE restaurant_id = 2 ORDER BY (o.id * id) % 7 LIMIT 3
) mi ON true
WHERE o.notes = 'PERFSEED';

-- limpieza
DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE notes='PERFSEED');
DELETE FROM orders WHERE notes='PERFSEED';
```

(Ajusta `restaurant_id`, `table_id` y `employee_id` a los IDs de tu propia base sembrada por el
`DataSeeder` en el primer arranque.)
