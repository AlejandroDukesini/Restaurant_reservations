-- Esquema base: reproduce el que Hibernate creo con ddl-auto=update en las instalaciones
-- existentes (tomado de pg_dump --schema-only), con los mismos tipos y nombres de
-- restricciones para que una base nueva y una adoptada queden identicas.
--
-- Solo se ejecuta en bases vacias. En una base existente Flyway registra la version 1
-- como linea base (spring.flyway.baseline-on-migrate) y NO ejecuta este script.
--
-- orders.status y reservations.status son enums ORDINAL (smallint): no reordenar sus valores.

CREATE TABLE restaurants (
    id bigserial NOT NULL,
    active boolean NOT NULL,
    address varchar(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    description varchar(255) NOT NULL,
    email varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    number_of_chairs integer NOT NULL,
    number_of_floors integer NOT NULL,
    number_of_tables integer NOT NULL,
    phone varchar(255) NOT NULL,
    slug varchar(255) NOT NULL,
    website_url varchar(255) NOT NULL,
    CONSTRAINT restaurants_pkey PRIMARY KEY (id),
    CONSTRAINT uk_hs57n29k4u6jfc5t978bq7wg9 UNIQUE (name),
    CONSTRAINT uk_bqfy5owwx6allqo3q7sfjk1il UNIQUE (slug)
);

CREATE TABLE users (
    id bigserial NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    email varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    password varchar(255) NOT NULL,
    role varchar(255) NOT NULL,
    restaurant_id bigint,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk_6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email),
    CONSTRAINT fksd7jrkn6c8wb1y8ffbdn3krel FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
);

CREATE TABLE zones (
    id bigserial NOT NULL,
    active boolean NOT NULL,
    code varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    sort_order integer NOT NULL,
    restaurant_id bigint NOT NULL,
    CONSTRAINT zones_pkey PRIMARY KEY (id),
    CONSTRAINT fkilpupnc9gbi0md0cgstd34h6x FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
);

CREATE TABLE tables (
    id bigserial NOT NULL,
    active boolean NOT NULL,
    capacity integer NOT NULL,
    floor integer NOT NULL,
    price double precision NOT NULL,
    table_number integer NOT NULL,
    restaurant_id bigint NOT NULL,
    gridx integer NOT NULL,
    gridy integer NOT NULL,
    status varchar(255) NOT NULL,
    version bigint,
    zone_id bigint,
    name varchar(255),
    CONSTRAINT tables_pkey PRIMARY KEY (id),
    CONSTRAINT tables_status_check CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE')),
    CONSTRAINT fk487cmd2iwf7o27dnxm3dahqur FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT fkh0mk4189qok025ejtijo3d1dv FOREIGN KEY (zone_id) REFERENCES zones (id)
);

CREATE TABLE menu_items (
    id bigserial NOT NULL,
    active boolean NOT NULL,
    category varchar(255) NOT NULL,
    condiments varchar(1000),
    description varchar(255),
    ingredients varchar(2000),
    name varchar(255) NOT NULL,
    preparation_notes varchar(1000),
    price double precision NOT NULL,
    protein varchar(255),
    restaurant_id bigint NOT NULL,
    CONSTRAINT menu_items_pkey PRIMARY KEY (id),
    CONSTRAINT menu_items_category_check CHECK (category IN ('STARTER', 'MAIN', 'DESSERT', 'DRINK')),
    CONSTRAINT fkhaqtoboitpl0n541y0sc753my FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
);

CREATE TABLE orders (
    id bigserial NOT NULL,
    notes varchar(255),
    order_date timestamp(6) without time zone NOT NULL,
    status smallint NOT NULL,
    total_amount double precision NOT NULL,
    employee_id bigint NOT NULL,
    table_id bigint NOT NULL,
    CONSTRAINT orders_pkey PRIMARY KEY (id),
    CONSTRAINT orders_status_check CHECK (status >= 0 AND status <= 3),
    CONSTRAINT fkgd67qo7p9pvyabrt03jamvni5 FOREIGN KEY (employee_id) REFERENCES users (id),
    CONSTRAINT fkrkhrp1dape261t3x3spj7l5ny FOREIGN KEY (table_id) REFERENCES tables (id)
);

CREATE TABLE order_items (
    id bigserial NOT NULL,
    item_name varchar(255) NOT NULL,
    notes varchar(255),
    price double precision NOT NULL,
    quantity integer NOT NULL,
    order_id bigint,
    status varchar(255) NOT NULL,
    menu_item_id bigint,
    CONSTRAINT order_items_pkey PRIMARY KEY (id),
    CONSTRAINT order_items_status_check CHECK (status IN ('PENDING', 'PREPARING', 'READY')),
    CONSTRAINT fkbioxgbv59vetrxe0ejfubep1w FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fkdtfg1f49yr5yye2fpl2xid2xo FOREIGN KEY (menu_item_id) REFERENCES menu_items (id)
);

CREATE TABLE reservations (
    id bigserial NOT NULL,
    confirmed boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    number_of_guests integer NOT NULL,
    reservation_date timestamp(6) without time zone NOT NULL,
    special_requests varchar(255),
    status smallint NOT NULL,
    customer_id bigint NOT NULL,
    table_id bigint NOT NULL,
    reservation_end timestamp(6) without time zone NOT NULL,
    CONSTRAINT reservations_pkey PRIMARY KEY (id),
    CONSTRAINT reservations_status_check CHECK (status >= 0 AND status <= 3),
    CONSTRAINT fkcmkyuub3ieebwbnrvh5710ply FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fkritru50ljg3q6ytxriivjlmq6 FOREIGN KEY (table_id) REFERENCES tables (id)
);

CREATE INDEX idx_reservation_table_date_status ON reservations USING btree (table_id, reservation_date, status);
