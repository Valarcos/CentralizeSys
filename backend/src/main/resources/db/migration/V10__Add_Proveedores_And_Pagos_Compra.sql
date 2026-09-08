-- V10: Add proveedor to productos and create pagos_compra table

ALTER TABLE productos ADD COLUMN proveedor VARCHAR(255);

CREATE TABLE pagos_compra (
                              id BIGSERIAL PRIMARY KEY,
                              compra_id BIGINT NOT NULL,
                              metodo_pago_id BIGINT NOT NULL,
                              monto DOUBLE PRECISION NOT NULL,
                              fecha_pago TIMESTAMP NOT NULL,
                              FOREIGN KEY (compra_id) REFERENCES compras(id) ON DELETE CASCADE,
                              FOREIGN KEY (metodo_pago_id) REFERENCES metodos_pago(id) ON DELETE RESTRICT
);

CREATE INDEX idx_pagos_compra_compra_id ON pagos_compra(compra_id);

-- Link gastos_caja to compras for Import Assistant

ALTER TABLE gastos_caja
    ADD COLUMN compra_id BIGINT NULL;

ALTER TABLE gastos_caja
    ADD CONSTRAINT fk_gastos_caja_compra
        FOREIGN KEY (compra_id) REFERENCES compras(id)
            ON DELETE SET NULL;
