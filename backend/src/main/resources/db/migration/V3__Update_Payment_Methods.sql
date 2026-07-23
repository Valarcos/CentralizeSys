-- V3__Update_Payment_Methods.sql
-- Synchronizes the metodos_pago table with the officially approved set of 15 payment methods.
-- Strategy: Soft-disable ALL existing active methods, then upsert the approved list.
-- This prevents DataIntegrityViolationException on historical sales that reference old method IDs.

-- Step 1: Disable all currently active payment methods.
UPDATE metodos_pago SET activo = FALSE;

-- Step 2: Insert the 15 approved and correctly-spelled methods.
-- ON CONFLICT (acronimo) DO UPDATE ensures idempotency and re-activates any method
-- that already existed under the same acronym with a corrected description.
INSERT INTO metodos_pago (acronimo, descripcion, activo) VALUES
     ('E',    'Efectivo',                           TRUE),
     ('TCM',  'Tarjeta Crédito Macro',              TRUE),
     ('TCBM', 'Tarjeta de Crédito BBVA MC',         TRUE),
     ('TCS',  'Tarjeta de Crédito Santander',       TRUE),
     ('DM',   'Débito Macro',                       TRUE),
     ('DBBVA','Débito BBVA',                        TRUE),
     ('TBVAF','Transferencia BBVA F',               TRUE),
     ('TBMF', 'Transferencia Mut. F',               TRUE),
     ('TCOM', 'Transferencia COMAFI',               TRUE),
     ('TBMP', 'Transferencia Mercado Pago M',       TRUE),
     ('TB3',  'Transferencia a Terceros',           TRUE),
     ('TBLE', 'Transferencia L. E.',                TRUE),
     ('TBLD', 'Transferencia L. D.',                TRUE),
     ('CHF',  'CHEQUE Físico',                      TRUE),
     ('ECH',  'E-Check',                            TRUE)
ON CONFLICT (acronimo) DO UPDATE
    SET activo     = TRUE,
        descripcion = EXCLUDED.descripcion;
