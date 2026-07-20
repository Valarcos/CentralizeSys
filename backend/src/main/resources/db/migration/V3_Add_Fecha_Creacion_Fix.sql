-- V3__Add_Fecha_Creacion_Fix.sql
-- Failsafe script to ensure fecha_creacion exists, in case the local database
-- was initialized before this column was added to the schema.

ALTER TABLE ventas ADD COLUMN IF NOT EXISTS fecha_creacion TIMESTAMP;

UPDATE ventas SET fecha_creacion = fecha WHERE fecha_creacion IS NULL;
