# CentralizeSys - Sistema de Gestión para Comercios

**Sistema de Punto de Venta e Inventario** diseñado para usuarios de edad avanzada, con interfaz de alto contraste y tipografía grande siguiendo estándares WCAG AAA.

---

## 🚀 Inicio Rápido

### Requisitos Previos

Antes de comenzar, asegúrese de tener instalado:

1. **Java 21 (LTS)** - [Descargar OpenJDK 21](https://adoptium.net/)
   ```bash
   java -version
   # Debe mostrar: java version "21.x.x"
   ```

2. **Node.js 20.19+ (LTS)** - [Descargar Node.js](https://nodejs.org/)
   ```bash
   node -version
   # Debe mostrar: v20.19.x o superior (Requerido por Vite 6+)
   ```

3. **Git** - [Descargar Git](https://git-scm.com/)
   ```bash
   git --version
   ```

---

## 📦 Instalación

### 1. Clonar el Repositorio

```bash
git clone https://github.com/Valarcos/sinpen_thesis.git
cd sinpen_thesis
```

### 2. Configurar Backend (Spring Boot + SQLite)

#### A. Verificar Gradle Wrapper

El proyecto incluye Gradle Wrapper - **no es necesario instalar Gradle manualmente**.

```bash
cd backend
```

#### B. Crear Directorios Necesarios

El backend requiere estos directorios (se crean automáticamente en el primer arranque, pero puede crearlos manualmente):

```bash
# PowerShell (Windows)
New-Item -ItemType Directory -Force -Path ..\data
New-Item -ItemType Directory -Force -Path ..\logs

# Bash (Linux/Mac)
mkdir -p ../data ../logs
```

#### C. Variables de Entorno (Opcional)

El backend funciona con valores por defecto. Para producción, configure:

```properties
# No es necesario crear archivo .env para desarrollo local
# El backend usa application.properties con valores predeterminados

# Para PRODUCCIÓN, configure estas variables de entorno:
# JWT_SECRET=<su-clave-secreta-de-512-bits>
# JWT_EXPIRATION_MS=604800000  # 7 días en milisegundos
# CORS_ALLOWED_ORIGINS=http://localhost:5173,https://su-dominio.com
```

#### D. Compilar y Ejecutar Backend

```bash
# Compilar el proyecto
./gradlew build

# Ejecutar servidor (http://localhost:8080)
./gradlew bootRun
```

**Verificación**: Abra un navegador en `http://localhost:8080/api/auth/login`

Si ve este mensaje JSON, el servidor está corriendo correctamente:
```json
{
  "status": 500,
  "message": "An unexpected error occurred: Request method 'GET' is not supported",
  "timestamp": ...
}
```

> **Nota**: Este error es esperado porque `/api/auth/login` solo acepta peticiones POST (el navegador envía GET). El frontend usa POST correctamente.

#### E. Ejecutar Tests

```bash
# Ejecutar todos los tests
./gradlew test

# Ver resultados en: backend/build/reports/tests/test/index.html
```

#### F. Logs y Depuración de Errores

El backend utiliza SLF4J para logging. **Todos los errores técnicos se registran en la consola**, mientras que los usuarios ven mensajes amigables en español.

##### ¿Dónde ver los logs?

1. **Consola de ejecución**: Al ejecutar `./gradlew bootRun` o desde IntelliJ, los logs aparecen en la terminal
2. **IntelliJ IDEA**: Ventana "Run" → pestaña "Console"
3. **Nivel de log**: Por defecto es `INFO`. Para más detalle, agregue a `application.properties`:
   ```properties
   logging.level.com.centralizesys=DEBUG
   ```

##### Formato de logs de error

```
2026-02-06 17:00:15 ERROR c.c.exception.GlobalExceptionHandler - DATA INTEGRITY VIOLATION: UNIQUE constraint failed: productos.codigo, precio_costo, precio_minorista
2026-02-06 17:00:16 ERROR c.c.exception.GlobalExceptionHandler - SQL SYNTAX ERROR - SQL: SELECT * FORM productos, Message: ...
2026-02-06 17:00:17 WARN  c.c.exception.GlobalExceptionHandler - DATABASE LOCK: Database is locked
```

##### Mapeo de errores SQLite → Excepciones Spring

El archivo `sql-error-codes.xml` traduce errores SQLite a excepciones Spring:

| Código SQLite | Significado | Excepción Spring |
|--------------|-------------|------------------|
| 19, 2067, 787, 1555 | Violación de constraints | `DataIntegrityViolationException` |
| 1 | Error de sintaxis SQL | `BadSqlGrammarException` |
| 5, 6 | Base de datos bloqueada | `CannotAcquireLockException` |
| 10, 11, 13, 14 | Error de disco/I/O | `DataAccessResourceFailureException` |

---


### 3. Configurar Frontend (React + Vite)

#### A. Navegar al Directorio Frontend

```bash
cd ../frontend
```

**Nota**: Si el directorio `frontend` no existe, se creará en Sprint 2. Para desarrollo actual, use solo el backend.

#### B. Instalar Dependencias

```bash
npm install
```

Esto instalará:
- React 19 & React Router v7
- Axios (cliente HTTP)
- React Hot Toast (notificaciones)
- **jsPDF & jsPDF-AutoTable** (Generación de Tickets PDF)
- **Vitest & Happy-DOM** (Testing Unitario)
- Vite (build tool)

> **Nota**: Si encuentra errores de dependencias faltantes (ej: `jspdf`), elimine `node_modules` y ejecute `npm install` nuevamente.

#### C. Crear Archivo de Variables de Entorno

Cree un archivo `.env` en `frontend/`:

```bash
# PowerShell
New-Item -ItemType File -Path .env

# Bash
touch .env
```

Contenido de `frontend/.env`:

```env
# URL del backend (desarrollo local)
VITE_API_URL=http://localhost:8080

# Para producción con Cloudflare Tunnel:
# VITE_API_URL=https://your-backend-tunnel.trycloudflare.com
```

**IMPORTANTE**: Vite requiere el prefijo `VITE_` para exponer variables al cliente.

#### D. Ejecutar Frontend

```bash
# Iniciar servidor de desarrollo (http://localhost:5173)
npm run dev
```

El servidor estará disponible en:
- Local: `http://localhost:5173`
- Red (LAN): `http://<su-ip-local>:5173`

---

## 🏃‍♂️ Ejecutar el Sistema Completo

### Opción 1: Dos Terminales

**Terminal 1 - Backend**:
```bash
cd backend
./gradlew bootRun
```

**Terminal 2 - Frontend**:
```bash
cd frontend
npm run dev
```

### Opción 2: Script de Inicio (Próximamente)

Se creará un script `start.bat` (Windows) / `start.sh` (Linux/Mac) para iniciar ambos servicios automáticamente.

---

## 👤 Usuarios de Prueba

### Usuario Administrador por Defecto

Al iniciar el backend por primera vez, se crea automáticamente un usuario administrador:

- **Email**: `marcosachavalmbaj@gmail.com`
- **Contraseña**: Configurada en la base de datos (`data/centralizesys.db`)
- **Rol**: `ADMIN`

**⚠️ IMPORTANTE**: Cambie la contraseña del administrador antes de desplegar en producción.

### Crear Nuevos Usuarios

Actualmente, los usuarios deben crearse directamente en la base de datos SQLite. Próximamente se agregará un endpoint de registro.

```sql
-- Abrir la base de datos con SQLite Browser o CLI
-- data/centralizesys.db

INSERT INTO usuarios (nombre, email, password_hash, rol)
VALUES ('Juan Pérez', 'juan@tienda.com', '$2a$10$<hash-bcrypt>', 'EMPLEADO');
```

Para generar un hash BCrypt, use:
```bash
# Con Node.js
npx bcrypt-cli <contraseña-en-texto-plano> 10
```

---

## 📂 Estructura del Proyecto

```
sinpen_thesis/
├── backend/                    # Spring Boot Application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/           # Código fuente Java
│   │   │   └── resources/
│   │   │       ├── schema.sql  # Esquema de base de datos
│   │   │       └── application.properties
│   │   └── test/               # Tests unitarios e integración
│   ├── build.gradle.kts        # Configuración Gradle
│   └── gradlew / gradlew.bat   # Gradle Wrapper
│
├── frontend/                   # React Application (Sprint 2+)
│   ├── src/
│   │   ├── components/         # Componentes reutilizables
│   │   ├── pages/              # Páginas principales
│   │   ├── services/           # Clientes API
│   │   └── layouts/            # Layouts de navegación
│   ├── .env                    # Variables de entorno
│   └── package.json            # Dependencias Node
│
├── data/                       # Base de datos SQLite
│   └── centralizesys.db
│
├── logs/                       # Archivos de log
│   └── app.log
│
├── docs/                       # Documentación
│   ├── architecture.md
│   ├── data-dictionary.md
│   └── domain-model.md
│
├── .cursorrules                # Reglas del proyecto
└── README.md                   # Este archivo
```

---

## 🔧 Configuración Avanzada

### Base de Datos (SQLite)

**Ubicación**: `data/centralizesys.db`

**Inicialización**: Se ejecuta automáticamente en el primer arranque del backend usando `schema.sql`.

**Backup Manual**:
```bash
# Copiar la base de datos
cp data/centralizesys.db data/centralizesys_backup_$(date +%Y%m%d).db
```

### CORS (Cross-Origin Resource Sharing)

El backend ya está configurado para aceptar peticiones de:
- `http://localhost:3000`
- `http://localhost:5173` (Vite)
- `https://*.trycloudflare.com` (Cloudflare Tunnels)
- `https://*.centralizesys.com`

Para agregar más orígenes, edite `backend/src/main/resources/application.properties`:

```properties
app.cors.allowed-origins=http://localhost:5173,http://nuevo-origen.com
```

### JWT (JSON Web Tokens)

**Configuración por defecto**:
- **Expiración**: 7 días (604800000 ms)
- **Algoritmo**: HS512
- **Secret**: Clave predeterminada (⚠️ cambiar en producción)

**Para producción**, configure variables de entorno:

```bash
# Windows PowerShell
$env:JWT_SECRET="su-clave-secreta-muy-larga-de-al-menos-512-bits"
$env:JWT_EXPIRATION_MS="604800000"

# Linux/Mac
export JWT_SECRET="su-clave-secreta-muy-larga-de-al-menos-512-bits"
export JWT_EXPIRATION_MS="604800000"
```

---

## 🌐 Despliegue para Clientes (Cloudflare Tunnel)

Para permitir que clientes accedan al sistema sin estar en su red local, necesita crear **dos túneles**: uno para el backend y otro para el frontend.

> **⚠️ IMPORTANTE**: Siga los pasos en el orden indicado. El frontend debe configurarse con la URL del backend **antes** de iniciarse.

### 1. Instalar Cloudflare Tunnel

```bash
# Windows (requiere winget)
winget install --id Cloudflare.cloudflared

# Mac
brew install cloudflare/cloudflare/cloudflared

# Linux
wget https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared-linux-amd64.deb
```

### 2. Iniciar el Backend y su Túnel

**Terminal 1 - Ejecutar Backend**:
```bash
cd backend
./gradlew bootRun
```

**Terminal 2 - Crear túnel para el Backend**:
```bash
cloudflared tunnel --url http://localhost:8080
```

Espere a que aparezca un mensaje similar a:
```
Your quick Tunnel has been created! Visit it at:
https://random-backend-name.trycloudflare.com
```

📋 **Copie esta URL del backend** - la necesitará en el siguiente paso.

### 3. Configurar el Frontend con la URL del Backend

Antes de iniciar el frontend, edite el archivo `frontend/.env`:

```env
# Reemplace con la URL generada en el paso anterior
VITE_API_URL=https://random-backend-name.trycloudflare.com
```

### 4. Iniciar el Frontend y su Túnel

**Terminal 3 - Ejecutar Frontend**:
```bash
cd frontend
npm run dev
```

**Terminal 4 - Crear túnel para el Frontend**:
```bash
cloudflared tunnel --url http://localhost:5173
```

Espere a que aparezca un mensaje similar a:
```
Your quick Tunnel has been created! Visit it at:
https://random-frontend-name.trycloudflare.com
```

### 5. Compartir con Clientes

Envíe a sus clientes la URL del **frontend** (ej: `https://random-frontend-name.trycloudflare.com`).

> **Nota**: Las URLs de Cloudflare Quick Tunnels son temporales y cambian cada vez que reinicia los túneles. Para URLs permanentes, considere usar [Cloudflare Named Tunnels](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/).

---

## 🧪 Testing

### Backend Tests

```bash
cd backend

# Ejecutar todos los tests
./gradlew test

# Ejecutar tests con salida detallada
./gradlew test --info

# Ver reporte HTML
# Abrir: backend/build/reports/tests/test/index.html
```

**Tests incluidos**:
- ✅ 173 tests (Sprint 1)
- Integration tests para todos los repositorios
- Unit tests para servicios críticos

### Frontend Tests (Sprint 4+)

```bash
cd frontend

# Unit tests (Vitest)
npm run test

# E2E tests (Playwright)
npm run test:e2e
```

---

## 📱 Compilar para Producción

### Backend

```bash
cd backend

# Crear JAR ejecutable
./gradlew bootJar

# El archivo estará en: build/libs/backend-0.0.2-SNAPSHOT.jar

# Ejecutar JAR
java -jar build/libs/backend-0.0.2-SNAPSHOT.jar
```

### Frontend

```bash
cd frontend

# Compilar para producción
npm run build

# Archivos optimizados en: dist/

# Vista previa del build
npm run preview
```

---

## 🛠️ Troubleshooting

### Backend no inicia

**Error: "Cannot find Java 21"**
- Verifique: `java -version`
- Descargue Java 21 de [Adoptium](https://adoptium.net/)

**Error: "Address already in use: 8080"**
- Otro proceso está usando el puerto 8080
- Solución: Detenga el otro proceso o cambie el puerto en `application.properties`:
  ```properties
  server.port=8081
  ```

### Frontend no inicia

**Error: "Cannot find module"**
- Ejecute: `npm install` nuevamente
- Verifique que Node.js 18+ esté instalado

  VITE_API_URL=http://localhost:8080
  ```

**Error: "Vite requires Node.js version X.X.X+"**
- Su versión de Node.js es antigua.
- **Solución**: Descargue e instale la última versión LTS desde [nodejs.org](https://nodejs.org/).

**Error: "Failed to resolve import..."**
- Dependencias corruptas o no instaladas.
- **Solución**:
  ```bash
  rm -rf node_modules package-lock.json
  npm install
  ```

### Tests fallan

**Error: "Database locked"**
- Cierre todas las conexiones a `data/centralizesys.db`
- Detenga el backend antes de ejecutar tests

**Error: "Constraint violation"**
- La base de datos tiene datos residuales
- Solución: Elimine `data/centralizesys.db` y reinicie el backend

### Login no funciona

**Error 401: "Credenciales incorrectas"**
- Verifique que el usuario existe en la tabla `usuarios`
- Verifique que la contraseña está hasheada con BCrypt

**Error CORS**
- Verifique que el frontend está en `http://localhost:5173`
- Revise la configuración CORS en `application.properties`

---

## 📝 Stack Tecnológico

### Backend
- **Java**: 21 (LTS)
- **Framework**: Spring Boot 3.3.3
- **Database**: SQLite (embedded)
- **Security**: Spring Security + JWT (HS512)
- **Build Tool**: Gradle 8.x (Kotlin DSL)
- **Testing**: JUnit 5, Mockito, AssertJ

### Frontend
- **Framework**: React 18
- **Build Tool**: Vite 6
- **Routing**: React Router DOM v6
- **HTTP Client**: Axios
- **Notifications**: React Hot Toast
- **Styling**: Vanilla CSS (WCAG AAA compliant)

---

## 🤝 Contribuir

Este proyecto es parte de una tesis de grado. No se aceptan contribuciones externas por el momento.

---

## 📄 Licencia

Proyecto privado - Todos los derechos reservados.

---

## 📧 Contacto

**Desarrollador**: Marcos Achaval  
**Email**: marcosachavalmbaj@gmail.com  
**GitHub**: [Valarcos](https://github.com/Valarcos)

---

## 🗺️ Roadmap

- [x] **Sprint 1**: Backend Foundation & Alerts (Complete)
- [ ] **Sprint 2**: Frontend Core & Authentication (En progreso)
- [ ] **Sprint 3**: Product Management & Dashboard
- [ ] **Sprint 4**: POS Transaction Flow
- [ ] **Sprint 5**: Stock Management & Reports
- [ ] **Sprint 6**: Debtor Management
- [ ] **Sprint 7**: Data Export & Backup
- [ ] **Sprint 8**: TiendaNube Integration

---

**Última actualización**: Febrero 2026 - Sprint 2
