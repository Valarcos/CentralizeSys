-- =========================================================================================
-- V11: External API Keys Table & Active Products Catalog Covering Index
-- =========================================================================================

CREATE TABLE IF NOT EXISTS api_keys (
                                        id SERIAL PRIMARY KEY,
                                        nombre VARCHAR(100) NOT NULL,
                                        key_hash VARCHAR(64) NOT NULL UNIQUE,
                                        activo BOOLEAN NOT NULL DEFAULT TRUE,
                                        creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        ultimo_acceso TIMESTAMP,
                                        rate_limit_rpm INTEGER NOT NULL DEFAULT 10
);

CREATE INDEX IF NOT EXISTS idx_api_keys_hash ON api_keys(key_hash) WHERE activo = true;
CREATE INDEX IF NOT EXISTS idx_productos_catalogo_active ON productos (codigo, cantidad_stock) WHERE activo = true;
