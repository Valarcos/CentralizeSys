# Registro de Deuda Técnica y Mejoras Arquitectónicas (Post-Hotfix)

**Proyecto:** `CentralizeSys`
**Fecha de Apertura:** Octubre 2026
**Clasificación:** Backlog de Mantenimiento Arquitectónico y Limpieza Estructural

Este documento consolida y cataloga formalmente aquellos hallazgos y áreas de oportunidad detectados durante la auditoría técnica del plan de hotfix consolidado (`docs/execution_plan.md`), cuya resolución fue postergada de mutuo acuerdo para priorizar la estabilidad operativa y evitar riesgos de regresión en producción.

---

## 1. Unificación de Zona Horaria en Serialización Jackson (Backend) — Decisión D-6

* **Severidad:** Media (Riesgo de regresión en clientes si se aplica de forma apresurada)
* **Componente Afectado:** `backend/src/main/java/com/centralizesys/config/WebMvcConfig.java` / `application.properties`
* **Descripción:**
  Actualmente coexisten dos formatos de serialización de marcas temporales hacia el frontend:
  1. Entidades JPA con `LocalDateTime` mapeadas con hora naive de Buenos Aires (ej. `"2026-10-20 02:55:00"` en `/api/ventas`).
  2. Consultas JDBC nativas con `java.sql.Timestamp` mapeadas por Jackson por defecto con offset UTC (ej. `"2026-10-20T05:55:00.000+00:00"` en `/api/cobros-y-pedidos`).

  Aunque el frontend fue blindado en este hotfix mediante `formatDate()` y `toDateKeyArgentina()` para interpretar ambos formatos de forma determinista, el backend debe unificar su contrato.
* **Plan de Acción:**
  1. Configurar en `application.properties`:
     ```properties
     spring.jackson.time-zone=America/Argentina/Buenos_Aires
     ```
     o registrar un bean `Jackson2ObjectMapperBuilderCustomizer`.
  2. Realizar una auditoría completa de los DTOs de salida (`VentaResponse`, `ClienteDto`, `Deudores`, `Cheques`, `Auditoria`).
  3. Ejecutar pruebas de regresión en todos los filtros de tablas y reportes de la UI.

---

## 2. Estandarización de Respuesta REST en Modo Mantenimiento

* **Severidad:** Baja
* **Componente Afectado:** `backend/src/main/java/com/centralizesys/config/MaintenanceInterceptor.java`
* **Descripción:**
  Cuando `isMaintenanceMode` se encuentra activo, el interceptor devuelve HTTP 503 mediante `response.getWriter().write("Service Unavailable - Maintenance Mode")` con tipo de contenido `text/plain`.
  Esto rompe la homogeneidad del contrato REST que estandariza las respuestas de error en el formato JSON `ErrorResponse(status, message, timestamp)`.
* **Plan de Acción:**
  1. Configurar el interceptor para escribir una respuesta JSON estructurada con cabecera `Content-Type: application/json;charset=UTF-8`.
  2. Serializar la instancia de `GlobalExceptionHandler.ErrorResponse`.

---

## 3. Homogeneización de Utilidades de Fecha en `GastosVariosSection.jsx`

* **Severidad:** Baja
* **Componente Afectado:** `frontend/src/components/GastosVariosSection.jsx` (L10-29)
* **Descripción:**
  El componente define sus propios formateadores locales (`getLocalDatetimeLocal` y `formatDisplayDate`) repitiendo el literal `'America/Argentina/Buenos_Aires'` y utilizando `hour12: false` (el cual en algunas versiones de navegadores puede emitir "24:00" a medianoche en lugar de "00:00").
* **Plan de Acción:**
  1. Reemplazar las funciones ad-hoc por las utilidades canónicas y probadas de `frontend/src/utils/format.js`:
     - Utilizar `TIMEZONE_ARGENTINA`.
     - Utilizar `formatTimeArgentina` o `formatDate` configurados con `hourCycle: 'h23'`.

---

## 4. Tipado Estricto de Filas en Consultas JDBC (`queryForList`)

* **Severidad:** Media (Deuda técnica de Clean Architecture / SonarQube)
* **Componentes Afectados:**
  - `backend/src/main/java/com/centralizesys/service/UnifiedViewService.java`
  - `backend/src/main/java/com/centralizesys/service/BackupService.java`
  - `backend/src/main/java/com/centralizesys/service/ReportesService.java`
* **Descripción:**
  Persisten retornos en crudo de `Map<String, Object>` provenientes de `jdbcTemplate.queryForList(...)` hacia la lógica de negocio y las respuestas HTTP, violando la regla de arquitectura `GEMINI.md §4 (Strict typing, no magic values)`.
* **Plan de Acción:**
  1. Definir Java 21 Records inmutables específicos para las proyecciones SQL (análogo a `SystemAlertDTO` y `DashboardSummaryResponse`).
  2. Emplear `RowMapper` tipados o consultas jOOQ/JPA para garantizar verificación estática en tiempo de compilación.

---

## 5. Limpieza de `console.error` Heredados en el Frontend

* **Severidad:** Baja (Higiene de código)
* **Componentes Afectados:**
  - `frontend/src/services/api.js` (L24, L55)
  - Vistas y modales heredados (`SalesHistoryPage.jsx`, `InventarioPage.jsx`, etc.)
* **Descripción:**
  Persisten llamadas a `console.error` en código de cliente que deberían canalizarse a través de un servicio de telemetría o eliminarse conforme a `GEMINI.md §4`.
* **Plan de Acción:**
  1. Integrar una regla estricta de ESLint (`no-console: ["error", { allow: ["warn", "error"] }]` o descarte total en producción con Vite `drop_console`).
