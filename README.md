# ApunteIA — BackEnd

API REST del proyecto **APUNTE IA** (curso EIF411 — Diseño y Programación de Plataformas Móviles).
Le da datos a la app Android: usuarios, cursos, apuntes y evaluaciones. Más adelante también hará de proxy seguro hacia la API de OpenAI.

```
📱 App Android ──HTTP/JSON──▶ ☁️ Backend Spring Boot ──▶ 🗄️ H2 (desarrollo) / PostgreSQL (Docker / nube)
                                      │
                                      └──(próximamente)──▶ 🤖 OpenAI
```

---

## 🧱 Stack

| Tecnología | Versión | Uso |
|---|---|---|
| Kotlin | 2.2.20 | Lenguaje |
| Spring Boot | 3.5.6 | Framework |
| Spring Web | (BOM de Boot) | Controladores REST, Tomcat embebido |
| Spring Data JPA + Hibernate | (BOM de Boot) | Persistencia |
| Bean Validation | (BOM de Boot) | Validación de DTOs (`@NotBlank`, `@Email`, ...) |
| Spring Security Crypto | (BOM de Boot) | Solo BCrypt para las contraseñas |
| H2 | (BOM de Boot) | Base local en archivo, sin instalar nada |
| PostgreSQL | 16 | Base en Docker y en la nube |
| Gradle (wrapper) | 8.14 | Build |
| Java | 17 | Runtime |
| Docker + Docker Compose | — | Levantar backend + PostgreSQL con un comando |
| Postman | — | Pruebas de integración de la API |

---

## 🏗️ Arquitectura

Arquitectura **en capas**. Cada capa solo habla con la de abajo:

```
HTTP (JSON)
   │
   ▼
controller/   → recibe la petición, valida el DTO (@Valid), delega. Sin lógica de negocio.
   │  DTOs
   ▼
service/      → reglas de negocio (@Transactional). Lanza ApiException si algo no se cumple.
   │  mapper/ convierte Entity ⇄ DTO
   ▼
repository/   → acceso a datos (Spring Data JPA)
   │
   ▼
entity/       → tablas de la base
```

Los errores **no** se manejan con `try/catch` en los controladores: el servicio lanza, por ejemplo,
`NotFoundException`, y `GlobalExceptionHandler` la convierte en un `404` con el formato común.

### Estructura de carpetas

```
src/main/kotlin/ApunteIA/BackEnd/
├── BackendApplication.kt
├── config/          PasswordConfig (bean BCrypt)
├── controller/      @RestController — AuthController, UserController, ...
├── dto/             Objetos de entrada/salida de la API (nunca se expone una @Entity)
│   ├── auth/        LoginRequest, LoginResponse, RegisterUserRequest
│   └── user/        UserResponse, UpdateUserStatusRequest
├── entity/          @Entity — User, Role, Privilege, Course, Enrollment, Note, Assessment, ...
├── exception/       ApiException (+ subclases), ErrorResponse, GlobalExceptionHandler
├── mapper/          UserMapper, ... (Entity ⇄ DTO)
├── repository/      Interfaces JpaRepository
├── seed/            Datos de prueba que se cargan al arrancar (Seeder + DataLoader con @Order)
└── service/         Interfaces de servicio
    └── impl/        Implementaciones con las reglas de negocio

src/main/resources/
├── application.yml            H2 en archivo (por defecto)
└── application-postgres.yml   PostgreSQL (perfil "postgres", lo usa Docker)

postman/
├── ApunteIA.postman_collection.json      Colección del equipo
└── ApunteIA-Local.postman_environment.json
```

---

## 🔌 Endpoints

### Autenticación y Usuarios

| Método | Ruta | Body | Respuestas |
|---|---|---|---|
| `POST` | `/auth/login` | `{ "email", "password" }` | `200` `{ token, userName, userId, email, role }` · `400` · `401` · `403` |
| `POST` | `/auth/register` | `{ "fullName", "email", "password" }` | `201` `UserResponse` + header `Location` · `400` · `409` |
| `GET` | `/users` | — (opcional `?status=ACTIVE\|BLOCKED\|DELETED`) | `200` `[UserResponse]` · `400` |
| `GET` | `/users/{id}` | — | `200` `UserResponse` · `400` · `404` |
| `PATCH` | `/users/{id}/status` | `{ "status": "BLOCKED" }` | `200` `UserResponse` · `400` · `404` · `409` |

**Reglas de negocio**

- El email se guarda y se busca en minúsculas y sin espacios.
- Las contraseñas se guardan con **BCrypt**; `UserResponse` nunca incluye `passwordHash`.
- Login: el mismo `401 "Credenciales inválidas"` para email inexistente y contraseña incorrecta (no revela qué emails existen).
- Login: cuenta `BLOCKED` → `403`; cuenta `DELETED` → `401`; cuenta de Google (sin contraseña) → `401`.
- Login exitoso: actualiza `last_login_at`.
- Registro: email duplicado → `409`; siempre se crea con rol `STUDENT`, privilegio `BASIC`, estado `ACTIVE`.
- Estado: `DELETED` es un **borrado lógico** y es final (no se puede reactivar → `409`). Pedir el mismo estado que ya tiene devuelve `200` sin cambios.
- El `token` por ahora es opaco (`apunteia-<uuid>`); en el módulo *Security with JWT* se reemplaza `TokenGenerator` por un JWT.

### Formato común de error

Todas las respuestas de error tienen esta forma:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "La petición tiene campos inválidos",
  "path": "/auth/register",
  "fieldErrors": { "email": "El email no tiene un formato válido" },
  "timestamp": "2026-10-11T14:30:00.123"
}
```

| Código | Cuándo |
|---|---|
| `400` | DTO inválido, JSON mal formado, enum inexistente, id no numérico |
| `401` | Credenciales inválidas |
| `403` | Cuenta bloqueada |
| `404` | Recurso o ruta inexistente |
| `405` | Método HTTP no permitido en esa ruta |
| `409` | Duplicado o transición de estado inválida |
| `415` | El `Content-Type` no es `application/json` |
| `500` | Error inesperado (el detalle solo queda en el log) |

**Para el resto del equipo:** en sus servicios lancen `NotFoundException`, `ConflictException`, `BadRequestException`, etc. (`exception/ApiExceptions.kt`) y el handler responde el código correcto solo.

---

## 👤 Usuarios de prueba

Los carga `IdentitySeeder` al arrancar (no se duplican si ya existen):

| Email | Contraseña | Rol / Privilegio | Estado |
|---|---|---|---|
| `demo@apunteia.com` | `123456` | STUDENT / BASIC | ACTIVE (el mismo del mock del FrontEnd) |
| `admin@apunteia.com` | `Admin123!` | ADMIN / FULL_ACCESS | ACTIVE |
| `profe@apunteia.com` | `Profe123!` | STUDENT / MANAGER | ACTIVE |
| `bloqueado@apunteia.com` | `123456` | STUDENT / BASIC | BLOCKED |
| `google.user@gmail.com` | — (Google) | STUDENT / BASIC | ACTIVE |

---

## ▶️ Correr en local (sin Docker)

Requisito: **JDK 17** o superior.

```bash
./gradlew bootRun          # macOS / Linux
gradlew.bat bootRun        # Windows
```

- API: <http://localhost:8080>
- Consola H2: <http://localhost:8080/h2-console> — JDBC URL `jdbc:h2:file:./data/apunteia`, usuario `sa`, sin contraseña.

Prueba rápida:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@apunteia.com","password":"123456"}'
```

---

## 🐳 Correr con Docker

Docker levanta **dos contenedores**: PostgreSQL 16 y el backend (perfil `postgres`). No hace falta tener Java ni PostgreSQL instalados.

### 1. Instalar Docker

- **macOS / Windows:** instalar [Docker Desktop](https://www.docker.com/products/docker-desktop/) y abrirlo (la ballena tiene que estar en verde).
- **Linux:** instalar Docker Engine y el plugin `docker compose`.

Verificar:

```bash
docker --version
docker compose version
```

### 2. (Opcional) Cambiar las credenciales de la base

```bash
cp .env.example .env     # y editar DB_USER / DB_PASSWORD
```

Si no se crea `.env`, se usan `postgres` / `postgres`.

### 3. Construir y levantar

Desde la carpeta raíz del backend (donde está `docker-compose.yml`):

```bash
docker compose up --build
```

La primera vez tarda unos minutos porque descarga las imágenes y las dependencias de Gradle. Está listo cuando en el log aparece:

```
apunteia-backend  | ... Tomcat started on port 8080 (http) ...
apunteia-backend  | ... Started BackendApplicationKt in ... seconds
```

Para dejarlo corriendo en segundo plano: `docker compose up --build -d`.

### 4. Probar

```bash
curl http://localhost:8080/users
```

o correr la colección de Postman (la misma `baseUrl` `http://localhost:8080` sirve para local y para Docker).

### Comandos útiles

| Comando | Para qué |
|---|---|
| `docker compose ps` | Ver los contenedores y su estado |
| `docker compose logs -f backend` | Ver el log del backend en vivo |
| `docker compose up --build -d` | Reconstruir después de cambiar código |
| `docker compose restart backend` | Reiniciar solo el backend |
| `docker compose down` | Detener todo (los datos de PostgreSQL **se conservan** en el volumen) |
| `docker compose down -v` | Detener y **borrar** la base (al volver a levantar se recargan los datos de prueba) |
| `docker compose up db -d` | Levantar **solo** PostgreSQL y correr el backend desde IntelliJ |
| `docker exec -it apunteia-db psql -U postgres -d apunteia` | Entrar a la base con `psql` (`\dt` lista las tablas, `\q` sale) |

**Correr el backend desde IntelliJ contra el PostgreSQL de Docker:** `docker compose up db -d` y en la configuración de ejecución poner la variable de entorno `SPRING_PROFILES_ACTIVE=postgres` (los valores por defecto de `application-postgres.yml` ya apuntan a `localhost:5432`).

### Archivos de Docker

| Archivo | Qué hace |
|---|---|
| `Dockerfile` | Build en 2 etapas: compila el `app.jar` con JDK 17 y lo corre en una imagen con solo el JRE, como usuario sin privilegios |
| `docker-compose.yml` | Define `db` (PostgreSQL con healthcheck y volumen) y `backend` (espera a que la base esté lista) |
| `.dockerignore` | Excluye `build/`, `.gradle/`, `data/`, `.idea/` del contexto de build |
| `.env.example` | Plantilla de credenciales; el `.env` real no se sube a git |

### Problemas comunes

- **`port is already allocated` (8080 o 5432):** ya hay algo corriendo en ese puerto (un `bootRun` abierto o un PostgreSQL local). Detenerlo, o cambiar el puerto de la izquierda en `docker-compose.yml` (por ejemplo `"8081:8080"`).
- **`./gradlew: not found` o `bad interpreter` al construir:** el archivo tiene saltos de línea de Windows; el `Dockerfile` ya los corrige, pero si persiste ejecutar `git config core.autocrlf input` y volver a clonar.
- **`Cannot connect to the Docker daemon`:** Docker Desktop no está abierto.
- **Cambié una entidad y la base quedó rara:** `docker compose down -v` y volver a levantar.

---

## 🧪 Pruebas

### Pruebas automáticas (JUnit)

```bash
./gradlew test
```

- `repository/*Test` — `@DataJpaTest` sobre H2 en memoria (capa de datos).
- `seed/*Test` — idempotencia de los datos de prueba.
- `controller/AuthUserControllerTest` — `@SpringBootTest` + `MockMvc`: prueba los endpoints de auth y usuarios de punta a punta (Lab 6).

### Postman

1. Postman → **Import** → seleccionar los dos archivos de la carpeta `postman/`.
2. Arriba a la derecha elegir el entorno **ApunteIA Local**.
3. Con el backend corriendo (local o Docker), clic derecho en la colección → **Run collection** → **Run**.
   Las peticiones se corren **en orden**: el registro guarda `{{newUserId}}` y `{{newEmail}}`, que usan las siguientes.

Cada petición trae tests que validan el status code, el header `Content-Type`, el formato de error y que nunca se exponga `passwordHash`.

Desde la terminal, con [Newman](https://www.npmjs.com/package/newman):

```bash
npx newman run postman/ApunteIA.postman_collection.json -e postman/ApunteIA-Local.postman_environment.json
```

Cada integrante agrega su carpeta a la misma colección (Cursos, Notas, Evaluaciones) y usa la variable `{{baseUrl}}`.

---

## 📱 Conectar el FrontEnd

`LoginResponse` respeta el contrato que ya usa la app (`{ token, userName }`), así que basta con cambiar la URL base de Retrofit:

- Emulador de Android: `http://10.0.2.2:8080/`
- Celular físico en la misma red: `http://<IP-de-la-compu>:8080/`

---

## 👥 Reparto — Lab 6 (Servicios, WebAPI, DTOs y Postman)

| Integrante | Módulo | Endpoints |
|---|---|---|
| Fernán | Autenticación y Usuarios + base común (capa web, manejo de errores, colección Postman, Docker) | `/auth/*`, `/users/*` |
| Alice | Cursos, Inscripciones y Auditoría | `/courses/*`, `/users/{id}/enrollments`, `/security-logs` |
| Sharon | Notas de estudio | `/notes/*` |
| Dayron | Evaluaciones e intentos | `/courses/{id}/assessments`, `/assessments/*`, `/attempts/*` |
