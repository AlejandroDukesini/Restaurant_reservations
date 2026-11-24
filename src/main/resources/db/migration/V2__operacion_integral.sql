-- V2: plataforma de operacion integral (grupos, personalizacion, mesas con sesiones de
-- atencion, pedidos trazables de mesa y domicilio, menu con categorias libres e imagenes,
-- mensajeria interna y notificaciones operativas).
--
-- Reglas de la migracion:
--  * No se borra ningun dato existente. Las categorias fijas del menu (enum) se convierten
--    en filas de menu_categories y cada plato queda enlazado a la suya.
--  * orders.status pasa de ORDINAL (smallint) a texto. Correspondencia del sistema anterior:
--    0 PENDING -> PENDING, 1 IN_PROGRESS -> PREPARING, 2 COMPLETED -> DELIVERED, 3 -> CANCELLED.
--    Los pedidos COMPLETED del sistema anterior quedan como entregados (no "listos"), para que
--    no aparezcan como pendientes de recoger. Sus marcas de tiempo nuevas quedan en NULL: no
--    se inventan tiempos que nunca se midieron, y las metricas los excluyen.
--  * payment_status de los pedidos anteriores queda UNPAID: no hay registro de cobro y no se
--    cuentan como ingreso definitivo.

-- ---------------------------------------------------------------- Archivos (imagenes)
CREATE TABLE media_files (
    id varchar(36) NOT NULL,
    restaurant_id bigint NOT NULL,
    content_type varchar(40) NOT NULL,
    size_bytes integer NOT NULL,
    width integer,
    height integer,
    data bytea NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by bigint,
    CONSTRAINT media_files_pkey PRIMARY KEY (id),
    CONSTRAINT fk_media_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT fk_media_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT media_files_type_check CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp'))
);

-- ---------------------------------------------------------------- Personalizacion del restaurante
ALTER TABLE restaurants ADD COLUMN theme text;
ALTER TABLE restaurants ADD COLUMN logo_media_id varchar(36);
ALTER TABLE restaurants ADD COLUMN cover_media_id varchar(36);
ALTER TABLE restaurants ADD CONSTRAINT fk_restaurant_logo FOREIGN KEY (logo_media_id) REFERENCES media_files (id) ON DELETE SET NULL;
ALTER TABLE restaurants ADD CONSTRAINT fk_restaurant_cover FOREIGN KEY (cover_media_id) REFERENCES media_files (id) ON DELETE SET NULL;

-- ---------------------------------------------------------------- Usuarios: permisos adicionales
-- Lista separada por comas de permisos de una lista blanca (ver Permission.java). Nunca
-- incluye capacidades exclusivas del gerente.
ALTER TABLE users ADD COLUMN permissions varchar(500);
ALTER TABLE users ADD COLUMN phone varchar(40);

-- ---------------------------------------------------------------- Grupos
CREATE TABLE staff_groups (
    id bigserial NOT NULL,
    restaurant_id bigint NOT NULL,
    name varchar(80) NOT NULL,
    description varchar(255),
    theme text,
    logo_media_id varchar(36),
    chat_enabled boolean NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT staff_groups_pkey PRIMARY KEY (id),
    CONSTRAINT fk_group_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT fk_group_logo FOREIGN KEY (logo_media_id) REFERENCES media_files (id) ON DELETE SET NULL
);

CREATE TABLE staff_group_members (
    group_id bigint NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT staff_group_members_pkey PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_member_group FOREIGN KEY (group_id) REFERENCES staff_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE user_devices (
    id bigserial NOT NULL,
    user_id bigint NOT NULL,
    device_key varchar(64) NOT NULL,
    label varchar(120),
    user_agent varchar(255),
    first_seen_at timestamp(6) without time zone NOT NULL,
    last_seen_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT user_devices_pkey PRIMARY KEY (id),
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_device_user_key UNIQUE (user_id, device_key)
);

-- ---------------------------------------------------------------- Mesas
ALTER TABLE tables ADD COLUMN group_id bigint;
ALTER TABLE tables ADD COLUMN sort_order integer NOT NULL DEFAULT 0;
ALTER TABLE tables ADD CONSTRAINT fk_table_group FOREIGN KEY (group_id) REFERENCES staff_groups (id) ON DELETE SET NULL;

-- Una sesion de atencion = una ocupacion de la mesa, de "ocupada" a "cerrada".
CREATE TABLE table_sessions (
    id bigserial NOT NULL,
    restaurant_id bigint NOT NULL,
    table_id bigint NOT NULL,
    table_label varchar(120) NOT NULL,
    waiter_id bigint,
    status varchar(20) NOT NULL,
    guests integer,
    opened_at timestamp(6) without time zone NOT NULL,
    attention_requested_at timestamp(6) without time zone,
    bill_requested_at timestamp(6) without time zone,
    closed_at timestamp(6) without time zone,
    closed_by bigint,
    version bigint,
    CONSTRAINT table_sessions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_session_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT fk_session_table FOREIGN KEY (table_id) REFERENCES tables (id),
    CONSTRAINT fk_session_waiter FOREIGN KEY (waiter_id) REFERENCES users (id),
    CONSTRAINT fk_session_closed_by FOREIGN KEY (closed_by) REFERENCES users (id),
    CONSTRAINT table_sessions_status_check CHECK (status IN ('OPEN', 'BILL_REQUESTED', 'CLOSED'))
);
-- Garantia en la base: nunca dos sesiones abiertas para la misma mesa (pedidos mezclados).
CREATE UNIQUE INDEX uk_table_sessions_one_open ON table_sessions (table_id) WHERE status <> 'CLOSED';
CREATE INDEX idx_table_sessions_restaurant_opened ON table_sessions (restaurant_id, opened_at);

-- ---------------------------------------------------------------- Menu: categorias libres
CREATE TABLE menu_categories (
    id bigserial NOT NULL,
    restaurant_id bigint NOT NULL,
    name varchar(80) NOT NULL,
    sort_order integer NOT NULL,
    active boolean NOT NULL,
    CONSTRAINT menu_categories_pkey PRIMARY KEY (id),
    CONSTRAINT fk_category_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
);

INSERT INTO menu_categories (restaurant_id, name, sort_order, active)
SELECT DISTINCT m.restaurant_id,
       CASE m.category WHEN 'STARTER' THEN 'Entradas' WHEN 'MAIN' THEN 'Platos fuertes'
                       WHEN 'DESSERT' THEN 'Postres' ELSE 'Bebidas' END,
       CASE m.category WHEN 'STARTER' THEN 1 WHEN 'MAIN' THEN 2 WHEN 'DESSERT' THEN 3 ELSE 4 END,
       true
FROM menu_items m;

ALTER TABLE menu_items ADD COLUMN category_id bigint;
UPDATE menu_items m SET category_id = c.id
FROM menu_categories c
WHERE c.restaurant_id = m.restaurant_id
  AND c.name = CASE m.category WHEN 'STARTER' THEN 'Entradas' WHEN 'MAIN' THEN 'Platos fuertes'
                               WHEN 'DESSERT' THEN 'Postres' ELSE 'Bebidas' END;
ALTER TABLE menu_items ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE menu_items ADD CONSTRAINT fk_menu_item_category FOREIGN KEY (category_id) REFERENCES menu_categories (id);
ALTER TABLE menu_items DROP CONSTRAINT menu_items_category_check;
ALTER TABLE menu_items DROP COLUMN category;

ALTER TABLE menu_items ALTER COLUMN description TYPE varchar(500);
ALTER TABLE menu_items ADD COLUMN image_media_id varchar(36);
ALTER TABLE menu_items ADD COLUMN prep_minutes integer;
ALTER TABLE menu_items ADD COLUMN available boolean NOT NULL DEFAULT true;
ALTER TABLE menu_items ADD CONSTRAINT fk_menu_item_image FOREIGN KEY (image_media_id) REFERENCES media_files (id) ON DELETE SET NULL;

-- ---------------------------------------------------------------- Domicilios
CREATE TABLE delivery_platforms (
    id bigserial NOT NULL,
    restaurant_id bigint NOT NULL,
    name varchar(60) NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT delivery_platforms_pkey PRIMARY KEY (id),
    CONSTRAINT fk_platform_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
);

CREATE TABLE delivery_drivers (
    id bigserial NOT NULL,
    restaurant_id bigint NOT NULL,
    name varchar(120) NOT NULL,
    phone varchar(40),
    user_id bigint,
    active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT delivery_drivers_pkey PRIMARY KEY (id),
    CONSTRAINT fk_driver_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT fk_driver_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_driver_user UNIQUE (user_id)
);

-- ---------------------------------------------------------------- Pedidos
ALTER TABLE orders ALTER COLUMN table_id DROP NOT NULL;
ALTER TABLE orders ADD COLUMN restaurant_id bigint;
UPDATE orders o SET restaurant_id = t.restaurant_id FROM tables t WHERE o.table_id = t.id;
ALTER TABLE orders ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT fk_order_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id);

ALTER TABLE orders DROP CONSTRAINT orders_status_check;
ALTER TABLE orders ALTER COLUMN status TYPE varchar(20) USING (
    CASE status WHEN 0 THEN 'PENDING' WHEN 1 THEN 'PREPARING' WHEN 2 THEN 'DELIVERED' ELSE 'CANCELLED' END
);
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (
    status IN ('PENDING', 'PREPARING', 'READY', 'PACKING', 'PACKED', 'ON_THE_WAY', 'DELIVERED', 'CANCELLED')
);

ALTER TABLE orders ADD COLUMN channel varchar(20) NOT NULL DEFAULT 'DINE_IN';
ALTER TABLE orders ADD CONSTRAINT orders_channel_check CHECK (channel IN ('DINE_IN', 'DELIVERY'));
ALTER TABLE orders ADD CONSTRAINT orders_target_check CHECK (channel = 'DELIVERY' OR table_id IS NOT NULL);
ALTER TABLE orders ADD COLUMN payment_status varchar(20) NOT NULL DEFAULT 'UNPAID';
ALTER TABLE orders ADD CONSTRAINT orders_payment_check CHECK (payment_status IN ('UNPAID', 'PAID'));

-- El DTO admitia 1000 caracteres de nota y la columna 255: en PostgreSQL la nota larga daba 500.
ALTER TABLE orders ALTER COLUMN notes TYPE varchar(1000);
ALTER TABLE order_items ALTER COLUMN notes TYPE varchar(500);

ALTER TABLE orders ADD COLUMN session_id bigint;
ALTER TABLE orders ADD CONSTRAINT fk_order_session FOREIGN KEY (session_id) REFERENCES table_sessions (id);
ALTER TABLE orders ADD COLUMN table_label varchar(120);
ALTER TABLE orders ADD COLUMN estimated_minutes integer;
ALTER TABLE orders ADD COLUMN preparing_at timestamp(6) without time zone;
ALTER TABLE orders ADD COLUMN ready_at timestamp(6) without time zone;
ALTER TABLE orders ADD COLUMN packing_at timestamp(6) without time zone;
ALTER TABLE orders ADD COLUMN packed_at timestamp(6) without time zone;
ALTER TABLE orders ADD COLUMN driver_assigned_at timestamp(6) without time zone;
ALTER TABLE orders ADD COLUMN dispatched_at timestamp(6) without time zone;
ALTER TABLE orders ADD COLUMN delivered_at timestamp(6) without time zone;
ALTER TABLE orders ADD COLUMN cancelled_at timestamp(6) without time zone;
ALTER TABLE orders ADD COLUMN cancel_reason varchar(255);
ALTER TABLE orders ADD COLUMN customer_name varchar(120);
ALTER TABLE orders ADD COLUMN customer_phone varchar(40);
ALTER TABLE orders ADD COLUMN delivery_address varchar(255);
ALTER TABLE orders ADD COLUMN external_ref varchar(60);
ALTER TABLE orders ADD COLUMN platform_id bigint;
ALTER TABLE orders ADD COLUMN driver_id bigint;
ALTER TABLE orders ADD COLUMN version bigint;
ALTER TABLE orders ADD CONSTRAINT fk_order_platform FOREIGN KEY (platform_id) REFERENCES delivery_platforms (id);
ALTER TABLE orders ADD CONSTRAINT fk_order_driver FOREIGN KEY (driver_id) REFERENCES delivery_drivers (id);

UPDATE orders o SET table_label = COALESCE(t.name, 'Mesa ' || t.table_number) FROM tables t WHERE o.table_id = t.id;

CREATE INDEX idx_orders_restaurant_date ON orders (restaurant_id, order_date);
CREATE INDEX idx_orders_restaurant_status ON orders (restaurant_id, status);
CREATE INDEX idx_orders_session ON orders (session_id);

-- Instantanea de categoria y tiempo estimado: las metricas historicas no cambian si luego
-- se renombra la categoria o se edita el plato.
ALTER TABLE order_items ADD COLUMN category_name varchar(80);
ALTER TABLE order_items ADD COLUMN prep_minutes integer;
CREATE INDEX idx_order_items_order ON order_items (order_id);

CREATE TABLE order_events (
    id bigserial NOT NULL,
    order_id bigint NOT NULL,
    type varchar(30) NOT NULL,
    detail varchar(255),
    actor_id bigint,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT order_events_pkey PRIMARY KEY (id),
    CONSTRAINT fk_event_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_event_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);
CREATE INDEX idx_order_events_order ON order_events (order_id);

-- ---------------------------------------------------------------- Mensajeria y notificaciones
CREATE TABLE chat_messages (
    id bigserial NOT NULL,
    restaurant_id bigint NOT NULL,
    group_id bigint NOT NULL,
    sender_id bigint NOT NULL,
    body varchar(1000) NOT NULL,
    ref_type varchar(20),
    ref_id bigint,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT chat_messages_pkey PRIMARY KEY (id),
    CONSTRAINT fk_message_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT fk_message_group FOREIGN KEY (group_id) REFERENCES staff_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users (id)
);
CREATE INDEX idx_chat_messages_group ON chat_messages (group_id, id);

CREATE TABLE chat_reads (
    id bigserial NOT NULL,
    group_id bigint NOT NULL,
    user_id bigint NOT NULL,
    last_read_id bigint NOT NULL,
    CONSTRAINT chat_reads_pkey PRIMARY KEY (id),
    CONSTRAINT fk_read_group FOREIGN KEY (group_id) REFERENCES staff_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_read_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_chat_read UNIQUE (group_id, user_id)
);

CREATE TABLE notifications (
    id bigserial NOT NULL,
    restaurant_id bigint NOT NULL,
    recipient_id bigint NOT NULL,
    type varchar(30) NOT NULL,
    title varchar(120) NOT NULL,
    body varchar(255),
    ref_type varchar(20),
    ref_id bigint,
    created_at timestamp(6) without time zone NOT NULL,
    read_at timestamp(6) without time zone,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT fk_notification_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES users (id)
);
CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, id);
