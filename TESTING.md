# Pruebas automatizadas

Guía de la suite de pruebas: qué se prueba, cómo ejecutarlo y qué resultados dio en la última
ejecución verificada. La auditoría y los defectos están en [`QA_AUDIT.md`](QA_AUDIT.md).

## Requisitos

| Componente | Requisito |
| --- | --- |
| Backend | JDK 21. Gradle 8.6 vía el wrapper (`gradlew.bat` en Windows, `./gradlew` en Linux/macOS). **No necesita PostgreSQL**: las pruebas usan H2 en memoria. |
| Frontend | Node.js ≥ 22.22.2 o ≥ 24.15 (lo exige jsdom 30, que usa Vitest) y pnpm 9.15.9, que Corepack toma de `packageManager` (`corepack pnpm …`). |

## Cómo ejecutar

```powershell
# Backend (Windows) — compila, ejecuta las pruebas y genera la cobertura JaCoCo
.\gradlew.bat test
# Solo una clase o un método
.\gradlew.bat test --tests "*ReservationApiIntegrationTest"
```

```bash
# Backend (Linux/macOS)
./gradlew test

# Frontend
cd site_web
corepack pnpm install --frozen-lockfile   # usa el pnpm fijado en packageManager (9.15.9)
pnpm test               # una pasada (CI)
pnpm test:watch         # modo interactivo
pnpm test:coverage      # con cobertura v8
```

Reportes que se generan (no se versionan):

| Reporte | Ruta |
| --- | --- |
| Resultados JUnit (HTML) | `build/reports/tests/test/index.html` |
| Cobertura JaCoCo (HTML / XML) | `build/reports/jacoco/test/html/index.html` · `build/reports/jacoco/test/jacocoTestReport.xml` |
| Cobertura Vitest (HTML / lcov) | `site_web/coverage/index.html` · `site_web/coverage/lcov.info` |

## Herramientas elegidas y por qué

| Herramienta | Motivo |
| --- | --- |
| JUnit 5 + Spring Boot Test + MockMvc + spring-security-test | Ya estaban declaradas en `build.gradle.kts` y son las nativas de Spring Boot 3.2. MockMvc ejecuta la cadena real de filtros de seguridad. |
| H2 en memoria (`testRuntimeOnly`) | Permite probar JPA, consultas y transacciones reales sin Docker ni PostgreSQL. Su versión la gestiona el BOM de Spring Boot. |
| JaCoCo 0.8.11 (plugin integrado de Gradle) | Primera versión con soporte oficial para bytecode de Java 21. |
| Vitest 5 + @vitest/coverage-v8 | Comparte la configuración y la transformación de Vite 8, que ya usa el proyecto. Vitest 5 declara compatibilidad con `vite ^8`. |
| Testing Library (React, user-event, jest-dom) + jsdom | Prueba la UI como la usa una persona (roles, etiquetas, clics), no detalles de implementación. |

No se añadieron librerías de mocks para Kotlin: las reglas de negocio se prueban contra la base de
datos real de pruebas, y las unidades aisladas no las necesitan.

## Diseño de la suite

- **Aislamiento**: el perfil `test` (`src/test/resources/application-test.yml`) usa H2, una clave
  JWT sintética, desactiva la siembra de demo y el limitador, y escribe los sitios generados en
  `build/`. Las pruebas de integración **no** son `@Transactional`: cada petición confirma su propia
  transacción, como en producción, y `IntegrationTest` vacía las tablas después de cada prueba. El
  resultado no depende del orden de ejecución.
- **Datos sintéticos**: `TestData` crea restaurantes, usuarios (dominio reservado
  `example.test`), mesas, zonas y platos. Ningún dato es real.
- **Autenticación real**: los tokens se firman con el `JwtTokenProvider` de la aplicación y pasan por
  `JwtAuthenticationFilter` y `CustomUserDetailsService`.
- **Fechas**: las reservas usan "hoy + 30 días a las 20:00", así que no dependen del día en que se
  ejecuten.
- **Regresiones de seguridad**: QA-SEC-01 y QA-SEC-02 se confirmaron con pruebas que describían el
  comportamiento correcto y fallaban (0 esperado / 1 obtenido; 401 esperado / 200 obtenido). Tras la
  corrección forman parte de `AdminManagementIntegrationTest` y `SecurityIntegrationTest`.

## Archivos de prueba

| Archivo | Tipo | Protege |
| --- | --- | --- |
| `src/test/.../security/JwtTokenProviderTest.kt` | Unitaria | Firma, expiración, emisor, `alg: none`, secreto débil, clave efímera |
| `src/test/.../security/RateLimitingFilterTest.kt` | Unitaria | Límite por IP, 429 + `Retry-After`, rutas exentas, `X-Forwarded-For` |
| `src/test/.../service/WebsiteGeneratorServiceTest.kt` | Unitaria | Escape HTML/JS (XSS) y path traversal en el slug |
| `src/test/.../dto/RequestValidationTest.kt` | Unitaria | Límites de Bean Validation de todos los DTO de entrada |
| `src/test/.../controller/AuthApiIntegrationTest.kt` | API | Registro (rol forzado, sin hash en la respuesta), login, no enumeración de cuentas |
| `src/test/.../security/SecurityIntegrationTest.kt` | API / seguridad | 401/403 por rol, cabeceras CSP/HSTS/etc., CORS, 404/405 |
| `src/test/.../controller/ReservationApiIntegrationTest.kt` | API / negocio | Aforo, solapamiento de franjas de 2 h, mapa, cancelación, IDOR |
| `src/test/.../controller/OrderKitchenFlowIntegrationTest.kt` | API / flujo E2E de servidor | Pedido → cola de cocina → estado derivado; aislamiento entre restaurantes |
| `src/test/.../controller/AdminManagementIntegrationTest.kt` | API / CRUD | Personal, menú, mesas, restaurantes; bajas lógicas; aislamiento |
| `src/test/.../config/DataSeederIntegrationTest.kt` | Persistencia | Contenido e idempotencia de la siembra; `SEED_ENABLED=false` |
| `site_web/src/api/client.test.js` | Unitaria UI | Cabeceras, query string, 401 → logout, mensajes de error, 204 |
| `site_web/src/utils/status.test.js` | Unitaria UI | Formato de importes; catálogos alineados con los enums del backend |
| `site_web/src/auth/AuthContext.test.jsx` | Componente | Sesión persistida, login sin guardar la contraseña, logout |
| `site_web/src/App.test.jsx` | Componente | Enrutado y redirecciones por rol |
| `site_web/src/pages/LoginPage.test.jsx` | Componente | Login correcto/incorrecto, cuentas demo, redirección con sesión |
| `site_web/src/pages/KitchenPage.test.jsx` | Componente | Cola con receta, estado vacío, marcar listo, error de la API |
| `site_web/src/pages/FloorPage.test.jsx` | Componente | Mapa por zonas, armado del pedido y total, envío, borrado, error |

## Matriz de trazabilidad

Estado: **A** = aprobada en la última ejecución · **P** = pendiente (sin prueba).

| ID | Funcionalidad | Componente | Tipo | Escenarios cubiertos | Resultado esperado | Estado |
| --- | --- | --- | --- | --- | --- | --- |
| F-01 | Login | `AuthService`, `AuthController` | API | correcto, mayúsculas/espacios, clave errónea, usuario inexistente, inactivo, JSON roto, cuerpo vacío | 200 + JWT válido / 401 idéntico / 400 | A |
| F-02 | Registro de cliente | `AuthService` | API | rol forzado a CUSTOMER, normalización, duplicado, clave corta | 200 sin hash / 400 | A |
| F-03 | JWT | `JwtTokenProvider` | Unitaria | ida y vuelta, firma/payload alterados, otra clave, caducado, emisor, `alg none`, secreto corto o vacío | válido solo si está íntegro y vigente | A |
| F-04 | RBAC | `SecurityConfig`, `@PreAuthorize` | API | 9 combinaciones rol → ruta prohibida, sin token, token inválido | 401 / 403 | A |
| F-05 | Cabeceras y CORS | `SecurityConfig`, `CorsConfig` | API | CSP, X-Frame-Options, nosniff, Referrer, COOP, Permissions; origen permitido y no permitido | cabeceras presentes; CORS 200/403 | A |
| F-06 | Limitador de peticiones | `RateLimitingFilter` | Unitaria | límite, 429, IP distintas, GET exento, desactivado, `X-Forwarded-For` | bloquea el exceso | A |
| F-07 | Reserva pública | `ReservationService` | API | válida, solape (dentro y en los bordes), contiguas, aforo, mantenimiento, otra sede, fecha pasada | 200 / 409 / 400 / 404 | A |
| F-08 | Mapa de disponibilidad | `ReservationService.getTableMap` | API | ocupada dentro de la franja, libre en los límites | OCCUPIED / AVAILABLE | A |
| F-09 | Reservas de cliente (IDOR) | `ReservationService.requireCanAccess` | API | dueño, otro cliente, personal propio y ajeno, cancelar libera la franja, mover a otra sede | 200 / 404 / 400 | A |
| F-10 | Pedidos | `OrderService` | API + UI | total calculado en el servidor, platos/mesa de otra sede, cantidades límite, autor vs admin, estados inválidos | 200 / 404 / 400 | A |
| F-11 | Cola de cocina | `CookService` | API + UI | receta visible, PREPARING → IN_PROGRESS, todos READY → COMPLETED, pedido cancelado no se reabre, cocina ajena | estado derivado correcto | A |
| F-12 | Personal | `StaffService` | API | alta, normalización, roles prohibidos, duplicados, edición sin clave, baja → sin login, otra sede | 200 / 400 | A |
| F-13 | Menú | `MenuService` | API | CRUD, baja lógica, categoría inválida, precio negativo, otra sede | 200 / 400 | A |
| F-14 | Mesas | `TableService` | API | CRUD, zona propia/ajena, baja lógica, lectura/borrado de otra sede | 200 / 400 / 404 | A |
| F-15 | Restaurantes | `RestaurantService` | API | registro + admin + sitio, nombre repetido o sin letras, email de admin repetido, acceso a otra sede, baja | 200 / 400 / 404 | A |
| F-16 | Sitio generado | `WebsiteGeneratorService` | Unitaria | archivos, XSS en campos, escape del slug en JS, `../` y slug vacío | escapado / rechazo | A |
| F-17 | Validación de entrada | DTO | Unitaria | límites de todos los DTO de entrada | violaciones exactas por campo | A |
| F-18 | Siembra de demo | `DataSeeder` | Persistencia | contenido, idempotencia, contraseña configurable, desactivada | 1 restaurante / 5 usuarios / 16 mesas / 10 platos | A |
| F-19 | Sesión en la SPA | `client.js`, `AuthContext` | UI | token en cabecera, 401 → logout + redirección, login sin redirección, persistencia | — | A |
| F-20 | Enrutado por rol | `App`, `ProtectedRoute` | UI | sin sesión, rol permitido/prohibido, ruta desconocida | redirección al panel del rol | A |
| F-21 | Vistas de admin (React) | `MenuAdmin`, `StaffAdmin`, `TablesAdmin`, `OrdersAdmin` | UI | — | — | **P** |
| S-01 | Admin limitado a su sede | `AdminService` | Regresión | pedidos, pedidos activos y reservas de otra sede no visibles, en ambos sentidos | solo lo propio | A (QA-SEC-01) |
| S-02 | Baja revoca el token | `CustomUserDetailsService` | Regresión | token válido antes de la baja, rechazado después | 200 → 401 | A (QA-SEC-02) |
| E-01 | Flujo completo en navegador | SPA + API + PostgreSQL | E2E | — | — | **P** (ver QA_AUDIT §7) |

## Resultados de la última ejecución verificada

Ejecución local en Windows 11, JDK 21.0.9, Node 24.16.0, 2026-10-04.

| Suite | Comando | Resultado |
| --- | --- | --- |
| Backend | `.\gradlew.bat clean build` | **115 pruebas: 115 aprobadas, 0 fallidas, 0 omitidas** |
| Frontend | `pnpm test:coverage` | **44 pruebas: 44 aprobadas** en 7 archivos |
| Build frontend | `pnpm build` | OK |

### Cobertura

| Métrica | Backend (JaCoCo 0.8.11) | Frontend (Vitest v8) |
| --- | --- | --- |
| Líneas | 97.1 % (1758/1811) | 53.1 % (214/403) |
| Ramas | 69.8 % (194/278) | 54.3 % (140/258) |
| Instrucciones / sentencias | 92.3 % | 52.5 % |
| Métodos / funciones | 90.5 % (552/610) | 42.5 % (65/153) |

Áreas con cobertura baja: los paneles de administración de React (F-21) y algunas ramas de
error poco frecuentes de los servicios.

### Comprobación de que las pruebas detectan fallos

1. **Antes de las correcciones**, la suite nueva falló en 13 pruebas de backend y 1 de frontend, todas
   por defectos reales (QA-BUG-01 a 06).
2. **Mutaciones controladas** (inyectadas y revertidas, archivos verificados con `cmp`):

   | Mutación | Prueba que la detectó |
   | --- | --- |
   | Solapamiento inclusivo (`<` → `<=`) en `existsOverlappingReservation` | `franjas contiguas no se solapan` |
   | Quitar el control de sede en `OrderService.getOrderById` | `solo el autor o un admin del restaurante modifican el pedido` |
   | `all` → `any` en el estado derivado de `CookService` | `la cocina ve los platos con su receta y el estado del pedido sigue a los platos` |

## Limitaciones conocidas de la suite

- **H2 no es PostgreSQL.** No se usa `MODE=PostgreSQL` porque rechaza el `TINYINT` que el dialecto
  H2 genera para los enums ordinales. No se verifican el bloqueo pesimista bajo concurrencia real
  ni los tipos exactos de PostgreSQL. Recomendado: Testcontainers en el CI.
- **Sin E2E en navegador.** El flujo pedido → cocina se verifica en la API (servidor completo) y
  en la UI (con la API simulada), pero no en un navegador contra el backend real.
- **El limitador de peticiones** se prueba en aislamiento y está desactivado en el perfil `test`.
