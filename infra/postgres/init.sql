-- Multiple logical databases in one Postgres instance.
-- Each bounded context owns its own database so schemas cannot leak between them.
-- Debezium later reads only the databases it needs (see ADR 0003).

CREATE DATABASE catalog_db;
CREATE DATABASE orders_db;

GRANT ALL PRIVILEGES ON DATABASE catalog_db TO ecommerce;
GRANT ALL PRIVILEGES ON DATABASE orders_db  TO ecommerce;
