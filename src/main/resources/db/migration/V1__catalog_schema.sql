-- Catalog bounded context.
-- Owns products and their categories. Stock is authoritative here; the cart
-- reads it directly and orders decrement it in a single transaction.

CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL UNIQUE,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku          VARCHAR(64)   NOT NULL UNIQUE,
    name         VARCHAR(200)  NOT NULL,
    description  TEXT          NOT NULL DEFAULT '',
    price        NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    stock        INTEGER       NOT NULL CHECK (stock >= 0),
    category_id  UUID          REFERENCES categories(id) ON DELETE SET NULL,
    active       BOOLEAN       NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version      BIGINT        NOT NULL DEFAULT 0        -- JPA optimistic locking
);

CREATE INDEX idx_products_active ON products(active);
CREATE INDEX idx_products_category ON products(category_id) WHERE category_id IS NOT NULL;
CREATE INDEX idx_products_name_trgm ON products USING gin (name gin_trgm_ops);

-- pg_trgm powers the trigram index above; created with IF NOT EXISTS so it's
-- safe on shared instances.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
