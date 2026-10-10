# Auditoría técnica y de calidad (QA)

Fecha de la auditoría: 2026-10-04 · Rama: `Beta` · Base: commit `7549bc2` más los cambios de
endurecimiento de seguridad que estaban sin commitear en el árbol de trabajo.

Este documento recoge **lo verificado en el código y en ejecuciones reales**. Lo que solo se
dedujo leyendo el código se marca como *(por inspección)*. La estrategia, los comandos y la
matriz de trazabilidad de las pruebas están en [`TESTING.md`](TESTING.md).

---

## 1. Tecnologías y versiones identificadas

| Componente | Tecnología | Versión (fuente) |
| --- | --- | --- |
| Backend | Kotlin / JVM | Kotlin 1.9.20, objetivo JVM 21 (`build.gradle.kts`) |
| | Spring Boot (Web, Data JPA, Security, Validation) | 3.2.0 |
| | Hibernate ORM | 6.3 (gestionado por Spring Boot 3.2.0) |
| | JWT | JJWT 0.12.3 (HS512) |
| | Base de datos | PostgreSQL (driver 42.7.1); esquema por `ddl-auto` |
| | Build | Gradle 8.6 — **solo `gradlew.bat`**, sin script `gradlew` para Linux/macOS (resuelto después: se añadió `gradlew`) |
| Frontend | React / React DOM | 19.2.7 (resuelto en `pnpm-lock.yaml`) |
| | React Router | 7.18.1 |
| | Vite / @vitejs/plugin-react | 8.1.4 / 6.0.3 |
| | Tailwind CSS | 4.3.2 (PostCSS) |
| | Gestor de paquetes | pnpm, lockfile v9 (el Dockerfile usa pnpm 9) |
| Infraestructura | Docker multi-etapa | `node:22-alpine` → `gradle:8.6-jdk21` → `eclipse-temurin:21-jre-alpine` |
| | Despliegue | Railway (`railway.toml`, builder Dockerfile) |
| | Rendimiento | Script k6 (`perf/cook-queue-load.js`) |

Herramientas **ausentes** antes de esta auditoría: pruebas automatizadas (0 archivos), cobertura,
CI/CD, linters/formateadores (ni ktlint/detekt ni ESLint/Prettier).

## 2. Arquitectura detectada

Monorepositorio con dos aplicaciones que se despliegan juntas:

- **API REST** Spring Boot en capas `controller → service → repository (Spring Data JPA)`, sin
  estado (JWT en cabecera `Authorization`), con control de acceso por rol en dos niveles: URL
  (`SecurityConfig`) y método (`@PreAuthorize`).
- **SPA React** (`site_web/`) para el personal. En producción el Dockerfile copia el build de Vite
  a `classpath:/static/` y `SpaWebConfig` sirve el SPA desde el mismo origen que la API.
- **Multi-tenant por restaurante**: cada `User` del personal pertenece a un `Restaurant`. El
  aislamiento se hace en los servicios comparando el `restaurantId` del token con el del recurso.
  Si no coincide, se responde 404 para no revelar si el recurso existe.

Puntos de integración frontend ↔ backend: `site_web/src/api/*.js` consume `/api/auth`, `/api/staff`,
`/api/employee`, `/api/cook` y `/api/admin`. **El SPA no consume los endpoints de reservas**: las
reservas existen solo como API.

## 3. Inventario funcional

| Funcionalidad | Estado | Evidencia |
| --- | --- | --- |
| Login JWT y registro de clientes | Implementada y probada | `AuthApiIntegrationTest` |
| RBAC (ADMIN, EMPLOYEE, COOK, CUSTOMER) | Implementada y probada | `SecurityIntegrationTest` |
| Aislamiento entre restaurantes | Implementada y probada (2 endpoints de admin corregidos: QA-SEC-01) | Varias suites |
| Pedidos por mesa (mesero) | Implementada y probada (API + UI) | `OrderKitchenFlowIntegrationTest`, `FloorPage.test.jsx` |
| Cola de cocina y estado derivado del pedido | Implementada y probada (API + UI) | `OrderKitchenFlowIntegrationTest`, `KitchenPage.test.jsx` |
| CRUD de mesas, menú y personal (admin) | Implementada; API probada, UI sin pruebas | `AdminManagementIntegrationTest` |
| Reservas (públicas y de cliente), mapa de disponibilidad | Implementada **solo en la API** (sin UI) y probada | `ReservationApiIntegrationTest` |
| Registro de restaurantes | **No funcionaba** (QA-BUG-01); corregida y probada | `AdminManagementIntegrationTest` |
| Generador de sitio web por restaurante | **Parcial**: escribe archivos que nada sirve (QA-FEAT-01) | `WebsiteGeneratorServiceTest` |
| Limitador de peticiones (login / público) | Implementada y probada en aislamiento | `RateLimitingFilterTest` |
| Siembra de datos de demo | Implementada y probada | `DataSeederIntegrationTest` |

## 4. Diagnóstico inicial (antes de modificar nada)

| Comprobación | Comando | Resultado |
| --- | --- | --- |
| Build backend | `.\gradlew.bat clean build` | OK; 13 advertencias del compilador (12 por APIs deprecadas de JJWT y 1 parámetro sin usar) |
| Pruebas backend | (incluido en `build`) | `NO-SOURCE`: no había pruebas |
| Build frontend | `pnpm install --frozen-lockfile && pnpm build` | OK |
| Pruebas frontend | — | No había framework ni pruebas |
| Auditoría de dependencias | `pnpm audit --registry=https://registry.npmjs.org` | 10 avisos (7 high, 3 moderate), ver QA-DEP-01 (hoy: 0) |
| Lint / análisis estático | — | No configurado; no se ejecutó (ver §7) |

El primer run de la suite nueva, **antes de corregir nada**, dio 111 pruebas con 13 fallos. Los 13
eran defectos reales del producto (QA-BUG-01…05). La UI dio 40 pruebas con 1 fallo (QA-BUG-06).

## 5. Defectos y riesgos

Severidad: **Alta** = pérdida de función principal o exposición de datos entre clientes ·
**Media** = fallo funcional acotado o debilidad de seguridad explotable con condiciones ·
**Baja** = inconsistencia o defecto sin impacto directo.

### 5.1 Corregidos en esta auditoría (con prueba de regresión)

| ID | Severidad | Defecto | Corrección | Prueba que lo protege |
| --- | --- | --- | --- | --- |
| QA-BUG-01 | Alta | `POST /api/admin/restaurants/register` respondía **siempre 500**: `Restaurant.websiteUrl` es `NOT NULL` y el servicio guardaba primero con `null`. La respuesta tampoco incluía la URL generada. | Se genera el sitio antes del único `save`. El email del admin se normaliza y se comprueba que no exista (antes daba otro 500). | `AdminManagementIntegrationTest` (registro y duplicados) |
| QA-BUG-02 | Media | El email del personal se guardaba tal cual, pero el login lo pasa a minúsculas: **una cuenta creada con mayúsculas nunca podía iniciar sesión**. Editar con un email ya usado daba 500. | `StaffService` normaliza (`trim().lowercase()`) y valida unicidad también al editar. | `...el email del personal se normaliza...`, `...email de otra cuenta responde 400...` |
| QA-BUG-03 | Media | JSON mal formado, cuerpo incompleto, parámetro ausente, id no numérico, método no soportado y ruta `/api` inexistente respondían **500** y se registraban como errores del servidor. | Handlers específicos en `GlobalExceptionHandler` (400/404/405) con el mismo formato `ApiErrorResponse`. | `AuthApiIntegrationTest`, `OrderKitchenFlowIntegrationTest`, `SecurityIntegrationTest` |
| QA-BUG-04 | Baja (falla en modo seguro) | `JwtTokenProvider.validateToken` dejaba escapar `SignatureException`: el `catch (SecurityException)` resolvía a `java.lang.SecurityException`. El filtro lo capturaba (no hay bypass) pero se registraba como ERROR con traza y se rompía el contrato `Boolean`. | `catch` de `io.jsonwebtoken.security.SecurityException`. | `JwtTokenProviderTest` (firma/payload alterados, otra clave) |
| QA-BUG-05 | Baja | `getRestaurantIdFromToken` devolvía siempre `null` (el claim llega como `Integer`). Hoy no se usa en producción. | Conversión vía `Number.toLong()`. | `JwtTokenProviderTest` |
| QA-BUG-06 | Baja | `LoginPage` llamaba a `navigate()` durante el render. React Router lo ignora, así que un usuario con sesión que abría `/login` se quedaba en la pantalla de login. | `return <Navigate … replace />`. | `LoginPage.test.jsx` |
| QA-SEC-01 | **Alta** | `GET /api/admin/orders`, `/api/admin/orders/active` y `/api/admin/reservations` devolvían `findAll()`: **un admin veía los pedidos y reservas (con nombres de clientes) de todos los restaurantes**. Confirmado con una prueba antes de corregir: esperaba 0, obtuvo 1. | `AdminService` filtra por el `restaurantId` del token (nuevo `OrderRepository.findByRestaurantIdAndStatus`). Corregido con autorización del propietario. **Cambio de contrato**: un admin ya no ve otros restaurantes. | `pedidos y reservas del panel admin se limitan al restaurante propio` |
| QA-SEC-02 | Media | Un JWT emitido antes de dar de baja a un usuario **seguía autenticando hasta expirar** (24 h por defecto): `loadUserById` no comprobaba `active`. Confirmado: esperaba 401, obtuvo 200. | `loadUserById` exige `active = true`. El filtro JWT registra ese caso como WARN, sin traza. Corregido con autorización. | `dar de baja a un usuario revoca su token aunque no haya expirado` |
| QA-REPO-01 | Media | `site_web/node_modules` (616 archivos) y `site_web/dist/index.html` estaban versionados pese al `.gitignore`. Cualquier `pnpm install` modificaba cientos de archivos rastreados y, al revés, restaurarlos rompía la instalación (git reemplaza los *junctions* de pnpm). | Eliminados de **todo el historial** de la rama `Beta` (con copia de seguridad previa), con autorización del propietario. Ver §9. | `git ls-files site_web/node_modules` → 0 |
| QA-DEP-01 | Media | `pnpm audit`: 7 high / 3 moderate (`react-router` 7.18.1 en runtime; postcss, nanoid, browserslist y baseline-browser-mapping en build). `package.json` usaba `"latest"` en todas las dependencias. | Rangos explícitos (`^x.y.z`); `react-router-dom` 7.18.4, `postcss` 8.5.28 y transitivas parcheadas, sin cambiar de versión mayor. `packageManager: pnpm@9.15.9`. | `pnpm audit` → *No known vulnerabilities found*; 44/44 pruebas UI y build OK |

### 5.2 Pendientes: requieren decisión (afectan a contratos, datos o despliegue)

| ID | Severidad | Hallazgo | Evidencia | Propuesta / riesgo |
| --- | --- | --- | --- | --- |
| QA-SEC-03 | Media | El limitador confía por defecto en `X-Forwarded-For` y toma el **primer** valor, que controla el cliente: rotar la cabecera evita el límite de login. *(por inspección + prueba que fija el comportamiento con `trust=false`)* | `RateLimitingFilter.clientIp` | Depende de cómo propaga la IP el proxy de Railway: usar la IP que añade el proxy (último salto de confianza) o `trust-forwarded-header=false`. |
| QA-SEC-04 | Media (despliegue) | La siembra está activa por defecto con la contraseña `password123`, que además aparece en la pantalla de login. | `application.yml`, `LoginPage.jsx` | En un entorno real: `SEED_ENABLED=false` o `SEED_PASSWORD` único. Ocultar la ayuda de demo fuera de la demo. |
| QA-SEC-05 | Decisión de diseño | Cualquier ADMIN puede crear restaurantes nuevos (`/api/admin/restaurants/register`). No existe un rol de plataforma. | `RestaurantController` | Definir quién puede dar de alta tenants. |
| QA-DATA-01 | Media | `Order.status` y `Reservation.status` se guardan como **ordinal** (sin `@Enumerated(STRING)`): reordenar o insertar un valor en el enum corrompe los datos existentes. | Modelos; DDL generado (`tinyint check (status between 0 and 3)`) | Migrar a `STRING` con un script de datos. |
| QA-DATA-02 | Media | Esquema gestionado con `ddl-auto: update`, sin migraciones versionadas. | `application.yml` | Introducir Flyway/Liquibase y `validate` en producción (ya lo recomienda el propio comentario del YAML). |

### 5.3 Otros hallazgos de menor severidad (no corregidos)

- **QA-API-01** · Códigos HTTP inconsistentes. Varios "no encontrado" lanzan `IllegalArgumentException`
  y responden 400 (menú, personal, cocina, `GET /api/public/restaurants/{slug}`). Una reserva solapada
  da 409 al crear pero 400 al editar. Una petición anónima a un método con `@PreAuthorize` recibe 403
  y no 401. Las pruebas fijan el comportamiento actual: si se cambia, hay que actualizarlas.
- **QA-API-02** · `updateReservation` no comprueba si la mesa está activa o en mantenimiento, ni usa
  el bloqueo pesimista que sí usa `createReservation` *(por inspección)*.
- **QA-API-03** · Reserva pública: el email del cliente no se normaliza. Si existe un usuario
  **inactivo** con ese email, el alta choca con la restricción `unique` y da 500. El cliente creado se
  asocia al restaurante, en contra del criterio de `AuthService.register` *(por inspección)*.
- **QA-API-04** · `MenuItemRequest` y `TableRequest` no tienen `@Size`. Un texto de más de 255
  caracteres llega a la base de datos y produce 500 *(por inspección)*.
- **QA-FEAT-01** · El generador de sitios escribe en `./generated-websites`, pero ningún handler sirve
  ese directorio. Su JavaScript lee `localStorage.token` (el SPA usa `mn.auth`) y carga Tailwind
  desde un CDN que la CSP `script-src 'self'` bloquearía.
- **QA-DOC-01** · El comentario de `CorsConfig` indica configurar los orígenes con
  `CORS_ALLOWED_ORIGINS`, pero esa variable no está mapeada. La que funciona, por *relaxed
  binding*, es `APP_CORS_ALLOWED_ORIGIN_PATTERNS`. Lo mismo ocurre con
  `APP_RATE_LIMIT_TRUST_FORWARDED_HEADER`. El README documenta los nombres correctos.
- **QA-BUILD-01** · No hay script `gradlew` para Unix y `gradle.properties` fija una ruta de JDK de
  Windows. El Dockerfile y el CI lo resuelven con Gradle 8.6 instalado aparte.
- **QA-CODE-01** · 12 advertencias por APIs deprecadas de JJWT 0.12 (`setSubject`, `signWith(key, alg)`,
  `parseClaimsJws`…). Funcionan, pero se eliminarán en JJWT 1.0.

## 6. Estrategia de pruebas aplicada

Resumen (detalle en [`TESTING.md`](TESTING.md)):

- **Unitarias** (sin Spring): JWT, limitador de peticiones, generador de sitios (XSS / path
  traversal) y validación de DTO.
- **Integración / API** (Spring completo + MockMvc + H2): autenticación, RBAC, aislamiento entre
  restaurantes, reglas de reservas, flujo de pedidos y cocina, CRUD de administración y siembra.
- **Componentes UI** (Vitest + Testing Library + jsdom): cliente HTTP, contexto de sesión,
  enrutado por rol, login, cocina y piso.
- **Regresión de defectos**: cada defecto corregido tiene su prueba. Los dos defectos de seguridad
  se confirmaron primero con pruebas que fallaban (esperaban el comportamiento correcto) y, tras la
  corrección autorizada, esas pruebas pasaron a formar parte de la suite activa.
- **Mutaciones controladas**: se inyectaron 3 defectos (solapamiento de franjas, IDOR en pedidos,
  estado del pedido) y la suite detectó los 3. Después se restauraron los archivos
  (comparados byte a byte con `cmp`).

## 7. Categorías descartadas y motivo

- **E2E con navegador**: requieren PostgreSQL y backend levantados. No hay Docker en el entorno de
  la auditoría y en el CI aumentaría mucho el tiempo. Los flujos críticos ya se cubren en las capas
  API y UI. Queda como siguiente paso (Playwright contra `docker compose`).
- **Linting / formato**: el proyecto no tiene configuración. Añadir ktlint/detekt/ESLint sin un
  baseline acordado generaría cientos de avisos de estilo ajenos a esta tarea. Recomendado como
  siguiente paso.
- **Pruebas contra PostgreSQL real** (Testcontainers): no hay Docker localmente. Las pruebas usan H2.
  Limitación conocida: H2 no reproduce exactamente el bloqueo pesimista ni los tipos de PostgreSQL.

## 8. Archivos creados o modificados por la auditoría

Ver el mensaje del commit de QA y [`TESTING.md`](TESTING.md#archivos-de-prueba).

## 9. Limpieza del historial (QA-REPO-01)

- Se eliminaron `site_web/node_modules/` y `site_web/dist/` de todos los commits de la rama `Beta`
  con `git filter-branch --index-filter 'git rm -r --cached --ignore-unmatch …' --prune-empty`.
  Los commits se reescriben: **cambian todos los hashes** desde el commit que los introdujo.
- Antes de reescribir se guardó una copia completa del repositorio con `git bundle create --all`.
- `origin/Principal` **no se modificó**: conserva el historial antiguo hasta que el propietario
  decida reemplazarlo (requiere `git push --force`). Mientras tanto, no conviene fusionar `Beta`
  en `Principal` con un *merge* normal, porque reintroduciría el historial antiguo: hay que
  reemplazar `Principal` o crear la PR después de reescribirla.
- Rendimiento de pnpm: el árbol de trabajo ya no mezcla dependencias instaladas con archivos
  versionados, y `packageManager` + `.npmrc` (`prefer-offline`, sin confirmación interactiva)
  evitan descargar pnpm y metadatos en cada instalación. Medición local puntual: reinstalación
  limpia desde el store en 29,5 s (antes, ~60 s con `npx pnpm@9`) y 7,6 s con `node_modules`
  ya presente.
