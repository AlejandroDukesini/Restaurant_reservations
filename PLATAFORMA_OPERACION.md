# Plataforma de operación del restaurante — informe técnico

Este documento resume la transformación del sistema de reservas en una plataforma de operación
(mesas, cocina, domicilios, mensajería, personalización, métricas y PWA), qué se verificó y qué
queda pendiente. Complementa `README.md`, `TESTING.md` y `QA_AUDIT.md`.

## 1. Arquitectura

### Antes
- **Backend**: Spring Boot 3.5 (Java 21), JWT sin estado, Spring Security con reglas por ruta y
  por método, JPA + PostgreSQL, Flyway (V1), limitador de peticiones propio.
- **Frontend**: React 19 + Vite + Tailwind 4 (SPA servida por el mismo JAR). Sin PWA.
- **Android**: app nativa Kotlin/Compose que consume la misma API.
- Roles: ADMIN (gerente de un restaurante), EMPLOYEE (mesero), COOK, CUSTOMER.
- Aislamiento por restaurante ya aplicado en servicios (404 ante recursos ajenos).
- Pedidos con estado ORDINAL de 4 valores, sin marcas de tiempo por etapa, sin sesiones de
  mesa (los pedidos de clientes sucesivos de una mesa se listaban juntos), categorías de menú
  fijas (enum), sincronización por polling cada 15 s solo en cocina.

### Después
Se conserva la misma arquitectura (sin reconstruir) y se extiende:

| Capa | Cambios |
|---|---|
| Base de datos | Migración `V2__operacion_integral.sql`: grupos y miembros, dispositivos, sesiones de mesa (índice único parcial: una sola atención abierta por mesa), categorías libres, imágenes (bytea), plataformas y domiciliarios, eventos de pedido, mensajes, lecturas y notificaciones. `orders.status` pasa a texto; nuevas marcas de tiempo y datos de domicilio. Migra datos existentes sin pérdida. |
| Backend | Servicios nuevos: `AccessService` (autorización de negocio), `RealtimeService` (SSE), `NotificationService`, `MediaService`, `BrandingService`, `GroupService`, `ChatService`, `FloorService`, `KitchenService`, `DeliveryService`, `MetricsService`. Controladores `OperationsController` y `ManagementController`. |
| Tiempo real | Server-Sent Events con señales sin datos de negocio emitidas tras el COMMIT; el cliente recarga por REST (la BD es la fuente de verdad). Reconexión con espera creciente, resincronización completa al reconectar y polling de respaldo cada 30 s. |
| Frontend | Tema dinámico por variables CSS, proveedor de tiempo real, navegación por rol/permiso (barra superior en escritorio, barra inferior en móvil), vistas nuevas: Mesas, Cocina, Domicilios, Mis entregas, Mensajes, Métricas y Gestión (Mesas, Menú, Personal, Grupos, Domicilios, Apariencia, Pedidos, Reservas). |
| PWA | `manifest.webmanifest`, iconos (incl. maskable), `sw.js`, aviso de versión nueva, botón de instalación, aviso sin conexión, notificaciones del sistema con la app abierta. |

## 2. Funcionalidades implementadas

### Roles y permisos
- Nuevo rol **DELIVERY** (domiciliario). ADMIN sigue siendo el gerente de un restaurante.
- **Permisos adicionales** que el gerente concede por empleado, de una lista blanca cerrada
  (`Permission.java`): `VIEW_METRICS`, `MANAGE_TABLES`, `TOGGLE_AVAILABILITY`,
  `MANAGE_DELIVERIES`, `CANCEL_ORDERS`, `KITCHEN_ACCESS`. Ninguno da funciones de gerente.
- Los permisos se leen de la base en cada petición (no viajan en el JWT ni en localStorage).

### Grupos
CRUD por el gerente, integrantes (solo personal activo del restaurante), chat propio que se
puede desactivar, identidad visual propia, actividad (mensajes y dispositivos de los
integrantes). Eliminar = archivar (se conserva el historial). Una mesa puede asignarse a un
grupo: solo sus integrantes la ven y operan (control en backend).

### Personalización
Paleta de 15 colores (primario, secundario, acento, fondos, tarjetas, textos, bordes,
navegación, botones y 4 estados), modo oscuro/claro, 5 paletas predefinidas, vista previa en
vivo en toda la app, restaurar predeterminado, logo (subir, reemplazar, quitar, tamaño y
posición; sin marcos artificiales), imagen de portada y nombre del restaurante. El **servidor
rechaza combinaciones ilegibles** (contraste WCAG AA). Resolución: tema del grupo del usuario →
tema del restaurante → predeterminado. Cada dispositivo puede además forzar oscuro/claro.

**Variantes oscuro/claro**: cambiar de modo (en el editor o desde la barra) genera la versión
de la misma paleta en el otro modo: fondos teñidos con el tono de la marca y cada color
reajustado en luminosidad hasta cumplir el contraste. Las 5 paletas tienen variante válida en
ambos modos (verificado por prueba).

### Perfil propio (migración V3)
Cada usuario, gerente incluido, tiene `/perfil`: nombre visible, foto de perfil (validada como
cualquier imagen y reemplazada sin dejar huérfanas) y colores personales, que tienen prioridad
sobre los del grupo y del restaurante y siguen al usuario en todos sus dispositivos. También
elige el modo de este dispositivo (según su tema / siempre oscuro / siempre claro). El perfil
opera siempre sobre el usuario del token: no permite cambiar correo, rol ni permisos.

### Confirmaciones
Los diálogos nativos del navegador (`confirm`/`prompt`) se reemplazaron por un diálogo propio
con el tema de la app: cierre de mesa con el importe a cobrar, cancelaciones con motivo y
eliminaciones.

### Mesas y flujo de sala
Sesión de atención: ocupar → pedidos → listo → entrega → pedir cuenta → cierre (cobro).
Estado visible derivado con prioridad única (Fuera de servicio > Requiere atención >
Pendiente de pago > Pedido listo > En preparación > Pedido enviado > Entregado > Ocupada >
Disponible). CRUD de mesas para gerente o `MANAGE_TABLES` (antes **cualquier mesero** podía
crear y borrar mesas). Eliminar una mesa con historial la desactiva.

### Pedidos y cocina
Transiciones validadas en el servidor (no se entrega lo que cocina no terminó; el progreso de
cocina solo avanza). Idempotencia (`Idempotency-Key`) contra doble envío. `@Version` contra
doble procesamiento. Instantánea de nombre, precio, categoría y tiempo estimado al pedir.
Historial `order_events`. Cocina por pedido con temporizador contra estimado y retrasos,
modificaciones por plato, "llamar mesero". Notificación **solo** al mesero del pedido / de la
mesa. El `DELETE` histórico de pedidos ahora **cancela** (no borra) para conservar trazabilidad.

### Menú
Categorías libres (CRUD), productos con imagen, tiempo estimado, disponibilidad (agotado),
archivar/activar, eliminación segura (si se pidió alguna vez se archiva).

### Domicilios
Flujo propio: cocina → empaque (inicio/fin) → esperando domiciliario / asignado → en camino →
entregado (cobrado). Plataformas libres, domiciliarios con o sin cuenta (solo cuentas DELIVERY
activas del restaurante). El domiciliario solo ve y mueve sus domicilios.

### Mensajería y notificaciones
Chat por grupo con no leídos, historial, referencias operativas (mesa/pedido) con enlace
directo, recuperación tras reconectar. Notificaciones dirigidas (listo, mesero solicitado,
atención de mesa, cancelación autorizada, domicilio asignado/esperando/entregado).

### Métricas (todas desde datos reales)
Definiciones en `MetricsService.java`. Mesa: espera en cocina, preparación, pedido→listo,
listo→entrega, total, atención inicial, cierre tras entrega, estancia. Domicilio: preparación,
empaque, espera de domiciliario, reparto, total. Ingresos cobrados (los cancelados nunca
suman; lo no cobrado se muestra aparte), ticket promedio, retrasados (real > estimado),
estimado vs real, más vendidos, categorías, por mesa/canal/plataforma, por domiciliario, por
empleado, tendencia diaria, comparación con el periodo anterior y cuellos de botella. Filtros:
hoy, 7 días, 30 días, personalizado. Cada cifra indica su muestra.

## 3. Seguridad (OWASP Top 10 / ASVS como referencia)

| Riesgo | Control |
|---|---|
| Broken Access Control / IDOR | Toda consulta se acota al restaurante del token; recursos ajenos → 404. Mesas por grupo, pedidos por autor/mesero/grupo, domicilios por domiciliario asignado, chat por integrantes. Reglas por ruta **y** en servicio. |
| Escalada de privilegios | No se crean cuentas ADMIN desde Personal; cuentas de gerente no editables por esa vía (ni la propia); permisos de lista blanca; rol y permisos leídos de la base en cada petición. |
| Falsificación de estados/métricas | Transiciones validadas, horas puestas por el servidor, precios copiados del menú, versión optimista. |
| XSS | Mensajes y nombres como texto (React escapa); CSP `script-src 'self'`; colores validados con regex (no se inyecta CSS); SVG prohibido en subidas. |
| Subida de archivos | Tipo por firma binaria (PNG/JPEG/WebP), peso ≤ 3 MB, dimensiones ≤ 6000 px leyendo solo la cabecera, solo gerente, servidas con su tipo y `nosniff`. |
| SQL injection | JPA con parámetros; las únicas sentencias JDBC usan parámetros. |
| CSRF | No aplica: token en cabecera `Authorization`, sesión sin estado. El canal SSE usa `fetch` con cabecera (el token no va en la URL). |
| Abuso | Límite por IP ampliado a mensajes (60/min) y subidas (20/min). |
| Concurrencia | Bloqueo de fila de mesa al abrir sesión + índice único parcial; `@Version` en pedidos y sesiones. |
| Registro | Intentos denegados (cross-tenant, permisos, grupos) quedan en log sin datos sensibles. |

Vulnerabilidades encontradas y corregidas durante la auditoría:
1. **Meseros podían crear, editar y eliminar mesas** (`/api/employee/tables` con rol EMPLOYEE): ahora exige gerente o `MANAGE_TABLES`.
2. **El mesero podía fijar cualquier estado de pedido** (p. ej. "COMPLETED" sin pasar por cocina) con `PUT /status`: ahora solo transiciones válidas.
3. **La cocina podía "reabrir" o mover platos de pedidos cancelados** (se aceptaba en silencio): ahora 409.
4. **Borrado físico de pedidos** eliminaba la trazabilidad: ahora cancela.
5. **Notas de pedido de hasta 1000 caracteres contra una columna de 255** (error 500 en PostgreSQL): columna ampliada.
6. **Pedidos de clientes anteriores mezclados** en la vista de una mesa: ahora solo la atención en curso.

No se declara una certificación de seguridad: la evaluación fue de código y pruebas
automatizadas, no un pentest externo.

## 4. Pruebas realizadas

| Suite | Resultado |
|---|---|
| Backend (JUnit + MockMvc, H2) | 142 pruebas, 0 fallos (2 omitidas: requieren PostgreSQL). Nuevas: `DineInFlowIntegrationTest`, `DeliveryAndMenuIntegrationTest`, `GroupsChatBrandingSecurityIntegrationTest`. |
| Migraciones en PostgreSQL 18 real | `PostgresSchemaIntegrationTest` 2/2 (Flyway V1+V2 y validación de Hibernate). V2 aplicada sobre una base V1 con datos: categorías y estados migrados correctamente. |
| Frontend (Vitest + Testing Library) | 68 pruebas, 0 fallos. Build de producción correcto. |
| E2E contra el JAR + PostgreSQL | 30/30 comprobaciones: flujo de mesa completo, idempotencia, SSE entre dos dispositivos, notificación dirigida, domicilio completo, bloqueos por rol, métricas exactas, tema por grupo. |

Lo que **no** se verificó en esta sesión: la interfaz en un navegador real o en un emulador
Android/Windows (instalación PWA, gestos táctiles, notificaciones del sistema). Está cubierta
por pruebas de componentes y por el build, pero conviene una revisión manual.

## 5. Limitaciones y pendientes

- **Una sola instancia**: conexiones SSE, caché de idempotencia y limitador viven en memoria.
  Para varias réplicas: bus compartido (PostgreSQL LISTEN/NOTIFY o Redis).
- **Notificaciones con la app cerrada** (Web Push con VAPID) no implementadas: los avisos del
  sistema funcionan con la app abierta o en segundo plano.
- **Offline**: solo abre la app y muestra imágenes ya vistas. Pedidos, estados y mensajes
  requieren conexión (decisión deliberada: no se muestran datos operativos viejos como actuales).
- **Zona horaria**: las horas se guardan en la zona del servidor. En Railway definir
  `TZ=America/Bogota` (o la que corresponda) para que "Hoy" coincida con el día local.
- "Recoger" y "entregar" en mesa son un solo paso (*Entregar*); el tiempo listo→entrega mide
  ambos.
- Pedidos anteriores a V2: `COMPLETED` pasa a `DELIVERED` sin marcas de tiempo y sin cobro
  registrado; no entran en tiempos ni ingresos.
- Las imágenes son públicas por URL (id aleatorio de 122 bits) porque `<img>` no envía el token.
- **App Android nativa**: solo se actualizaron etiquetas de estado; sigue funcionando con los
  endpoints existentes, pero grupos, chat, domicilios y métricas están solo en la PWA.
- Pagos: un único estado cobrado/no cobrado; no hay pasarela, propinas ni división de cuenta.
