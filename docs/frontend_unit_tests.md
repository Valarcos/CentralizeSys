# Especificación y Guía de Pruebas Unitarias de Frontend (Vitest & Happy-DOM)

## 1. Contexto y Objetivos

Este documento formaliza la especificación técnica, casos de borde y suites completas de pruebas unitarias para las utilidades críticas de frontend desarrolladas en la plataforma (`CentralizeSys`):
1. **`frontend/src/utils/format.js`**: Aritmética de fechas civiles argentinas e inmunidad a zonas horarias y desfasaje UTC-3 nocturno (21:00–23:59).
2. **`frontend/src/utils/apiError.js`**: Normalización y extracción segura de mensajes de error de red y backend (Axios).
3. **`frontend/src/utils/uiGuards.js`**: Guarda universal de interfaz de usuario para pausar polling y revalidación en presencia de modales (`.modal-overlay`) o pestaña oculta (`document.hidden`).

> [!NOTE]
> Dado que la suite de pruebas automatizadas de frontend no se encuentra aún desplegada en el pipeline de CI/CD del proyecto, estas especificaciones y snippets quedan archivados y estandarizados aquí para su posterior incorporación cuando se implemente la fase integral de testing en frontend.

---

## 2. Configuración del Entorno de Pruebas

El entorno configurado en `frontend/vite.config.js` y `frontend/package.json` ya dispone de:
- **Framework de Tests:** Vitest (`vitest`)
- **Entorno DOM:** Happy-DOM (`happy-dom`)
- **Setup global:** `frontend/src/setupTests.js` (`import '@testing-library/jest-dom';`)

### Ejecución de Pruebas (cuando se activen)
```powershell
# En el directorio frontend/:
npm test -- --run

# Verificación de invariancia ante zonas horarias extranjeras (simulando dispositivo en Chile):
$env:TZ='America/Santiago'; npm test -- --run src/utils/format.test.js; Remove-Item Env:TZ
```

---

## 3. Especificación de Pruebas Unitarias

### 3.1 `frontend/src/utils/format.test.js`

Evalúa la invariancia horaria de Argentina (`America/Argentina/Buenos_Aires`), aritmética civil con `Date.UTC`, formateo naive vs. strings con offset ISO, y estabilidad ante llamadas nulas/inválidas.

```javascript
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
    getTodayArgentina,
    getDaysAgoArgentina,
    toDateKeyArgentina,
    formatDate,
    formatDateOnly,
    formatTimeArgentina,
    TIMEZONE_ARGENTINA
} from './format';

describe('format.js - Utilidades de Fecha Comercial Argentina', () => {

    afterEach(() => {
        vi.useRealTimers();
    });

    describe('getTodayArgentina', () => {
        it('devuelve la fecha civil de Argentina entre las 21:00 y 23:59 UTC-3 (evita salto UTC a mañana)', () => {
            // 2026-10-21 01:30:00 UTC equivale a las 22:30:00 del 2026-10-20 en Argentina
            vi.useFakeTimers();
            vi.setSystemTime(new Date('2026-10-21T01:30:00Z'));

            expect(getTodayArgentina()).toBe('2026-10-20');
        });

        it('devuelve la fecha civil correcta al mediodía', () => {
            vi.useFakeTimers();
            vi.setSystemTime(new Date('2026-10-20T15:00:00Z'));

            expect(getTodayArgentina()).toBe('2026-10-20');
        });

        it('maneja correctamente el cruce de fin de mes', () => {
            // 2026-11-01 01:00:00 UTC equivale a las 22:00:00 del 2026-10-31 en Argentina
            vi.useFakeTimers();
            vi.setSystemTime(new Date('2026-11-01T01:00:00Z'));

            expect(getTodayArgentina()).toBe('2026-10-31');
        });
    });

    describe('getDaysAgoArgentina', () => {
        it('calcula exactamente N días corridos hacia atrás sin verse afectado por DST', () => {
            vi.useFakeTimers();
            vi.setSystemTime(new Date('2026-10-20T15:00:00Z')); // Hoy = 2026-10-20

            expect(getDaysAgoArgentina(30)).toBe('2026-09-20');
        });

        it('calcula retrocesos con cruce de año calendario', () => {
            vi.useFakeTimers();
            vi.setSystemTime(new Date('2026-01-05T15:00:00Z')); // Hoy = 2026-01-05

            expect(getDaysAgoArgentina(10)).toBe('2025-12-26');
        });
    });

    describe('toDateKeyArgentina', () => {
        it('convierte strings con offset explícito Z o UTC a fecha argentina', () => {
            // 2026-10-21T01:30:00Z -> en Argentina sigue siendo 2026-10-20
            expect(toDateKeyArgentina('2026-10-21T01:30:00Z')).toBe('2026-10-20');
        });

        it('mantiene la fecha tal cual para strings naive (hora local ya en Argentina)', () => {
            expect(toDateKeyArgentina('2026-10-20 02:55:00')).toBe('2026-10-20');
            expect(toDateKeyArgentina('2026-10-20')).toBe('2026-10-20');
        });

        it('retorna null para formatos completamente inválidos o nulos', () => {
            expect(toDateKeyArgentina(null)).toBeNull();
            expect(toDateKeyArgentina('')).toBeNull();
            expect(toDateKeyArgentina('invalido')).toBeNull();
        });
    });

    describe('formatDate', () => {
        it('formatea strings ISO con offset UTC a hora comercial argentina', () => {
            // 05:55 UTC = 02:55 en Buenos Aires (UTC-3)
            expect(formatDate('2026-10-20T05:55:00.000+00:00')).toBe('02:55:00 - 20/10/2026');
        });

        it('formatea strings ISO con Z nocturna a hora de Argentina', () => {
            // 01:30:00Z del día 21 = 22:30:00 del día 20 en Argentina
            expect(formatDate('2026-10-21T01:30:00Z')).toBe('22:30:00 - 20/10/2026');
        });

        it('formatea strings naive de base de datos directamente', () => {
            expect(formatDate('2026-10-20 02:55:00')).toBe('02:55:00 - 20/10/2026');
        });

        it('formatea strings de solo fecha agregando 00:00:00', () => {
            expect(formatDate('2026-10-20')).toBe('00:00:00 - 20/10/2026');
        });

        it('maneja cadenas vacías o no válidas retornando el valor seguro', () => {
            expect(formatDate('')).toBe('');
            expect(formatDate(null)).toBe('');
            expect(formatDate('no-es-fecha')).toBe('no-es-fecha');
        });
    });

    describe('formatDateOnly', () => {
        it('formatea fechas con offset convirtiendo primero al día civil argentino', () => {
            expect(formatDateOnly('2026-10-21T01:30:00Z')).toBe('20/10/2026');
        });

        it('formatea fechas naive o solo fecha a DD/MM/YYYY', () => {
            expect(formatDateOnly('2026-10-20 02:55:00')).toBe('20/10/2026');
            expect(formatDateOnly('2026-10-20')).toBe('20/10/2026');
        });

        it('retorna vacío para valores nulos o vacíos', () => {
            expect(formatDateOnly('')).toBe('');
            expect(formatDateOnly(null)).toBe('');
        });
    });

    describe('formatTimeArgentina', () => {
        it('formatea un objeto Date en hora argentina de 24h (h23)', () => {
            const date = new Date('2026-10-20T18:05:00Z'); // 15:05 en Argentina
            expect(formatTimeArgentina(date)).toBe('15:05');
        });
    });
});
```

---

### 3.2 `frontend/src/utils/apiError.test.js`

Evalúa la extracción de mensajes de error de Axios conforme a las respuestas REST (`ErrorResponse`) del backend.

```javascript
import { describe, it, expect } from 'vitest';
import { getApiErrorMessage } from './apiError';

describe('apiError.js - Normalización de Errores de API', () => {
    it('retorna mensaje de error de conexión si el error no tiene respuesta (falla de red o timeout)', () => {
        const networkError = new Error('Network Error');
        const message = getApiErrorMessage(networkError);
        expect(message).toBe('No se pudo conectar con el servidor. Verifique su conexión e intente nuevamente.');
    });

    it('extrae el campo message de un payload estructurado de backend', () => {
        const error = {
            response: {
                data: {
                    status: 400,
                    message: 'El cliente tiene deudas vencidas.',
                    timestamp: 1729450000000
                }
            }
        };
        expect(getApiErrorMessage(error)).toBe('El cliente tiene deudas vencidas.');
    });

    it('retorna el texto crudo si la respuesta data es una cadena de texto simple', () => {
        const error = {
            response: {
                data: 'Servicio no disponible temporalmente.'
            }
        };
        expect(getApiErrorMessage(error)).toBe('Servicio no disponible temporalmente.');
    });

    it('retorna el fallback si data es un objeto sin campo message o con message vacío', () => {
        const errorEmptyMessage = {
            response: {
                data: { message: '   ' }
            }
        };
        expect(getApiErrorMessage(errorEmptyMessage, 'Fallo genérico')).toBe('Fallo genérico');

        const errorNoMessage = {
            response: {
                data: {}
            }
        };
        expect(getApiErrorMessage(errorNoMessage, 'Fallo genérico')).toBe('Fallo genérico');
    });

    it('maneja valores de error nulos o indefinidos de forma segura', () => {
        expect(getApiErrorMessage(undefined)).toBe('No se pudo conectar con el servidor. Verifique su conexión e intente nuevamente.');
        expect(getApiErrorMessage(null)).toBe('No se pudo conectar con el servidor. Verifique su conexión e intente nuevamente.');
    });
});
```

---

### 3.3 `frontend/src/utils/uiGuards.test.js`

Evalúa la detección universal de interacción de usuario en el DOM y visibilidad del documento.

```javascript
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { isUserInteracting } from './uiGuards';

describe('uiGuards.js - Guarda Universal de UI', () => {

    beforeEach(() => {
        document.body.innerHTML = '';
        Object.defineProperty(document, 'hidden', { value: false, configurable: true });
    });

    afterEach(() => {
        document.body.innerHTML = '';
        Object.defineProperty(document, 'hidden', { value: false, configurable: true });
    });

    it('retorna false cuando la pestaña está visible y no hay modales en el DOM', () => {
        expect(isUserInteracting()).toBe(false);
    });

    it('retorna true cuando existe un modal montado (.modal-overlay)', () => {
        const modal = document.createElement('div');
        modal.className = 'modal-overlay';
        document.body.appendChild(modal);

        expect(isUserInteracting()).toBe(true);
    });

    it('retorna true cuando la pestaña está oculta (document.hidden = true) sin importar los modales', () => {
        Object.defineProperty(document, 'hidden', { value: true, configurable: true });

        expect(isUserInteracting()).toBe(true);
    });

    it('retorna false en entornos donde document es indefinido (seguridad SSR)', () => {
        const originalDoc = global.document;
        try {
            // @ts-ignore
            delete global.document;
            expect(isUserInteracting()).toBe(false);
        } finally {
            global.document = originalDoc;
        }
    });
});
```
