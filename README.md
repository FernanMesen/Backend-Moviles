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
| Spring Security | (BOM de Boot) | Autenticación con JWT, roles y BCrypt |
| JJWT | 0.12.6 | Generar y validar los JWT (HS256) |
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
| `POST` | `/auth/login` | `{ "email", "password" }` | `200` `{ token, tokenType, expiresIn, userName, userId, email, role }` · `400` · `401` · `403` |
| `POST` | `/auth/register` | `{ "fullName", "email", "password" }` | `201` `UserResponse` + header `Location` · `400` · `409` |
| `POST` | `/auth/forgot-password` | `{ "email" }` | `200` mensaje genérico; envía enlace si la cuenta local existe |
| `POST` | `/auth/reset-password` | `{ "token", "newPassword" }` | `204` · `400` token inválido/vencido |
| `PUT` | `/auth/password` | `{ "currentPassword", "newPassword" }` | `204` · `400` |
| `GET` | `/users` | — (opcional `?status=ACTIVE\|BLOCKED\|DELETED`) | `200` `[UserResponse]` · `400` |
| `GET` | `/users/{id}` | — | `200` `UserResponse` · `400` · `404` |
| `PATCH` | `/users/{id}/status` | `{ "status": "BLOCKED" }` | `200` `UserResponse` · `400` · `404` · `409` |
| `GET` | `/users/me/export?format=json, pdf o csv` | — (por defecto `json`) | `200` archivo descargable con datos de la cuenta autenticada · `400` formato no soportado |
| `DELETE` | `/users/me` | — | `204`; eliminación definitiva de cuenta y datos personales asociados |

**Reglas de negocio**

- El email se guarda y se busca en minúsculas y sin espacios.
- Las contraseñas se guardan con **BCrypt**; `UserResponse` nunca incluye `passwordHash`.
- Login: el mismo `401 "Credenciales inválidas"` para email inexistente y contraseña incorrecta (no revela qué emails existen).
- Login: cuenta `BLOCKED` → `403`; cuenta `DELETED` → `401`; cuenta de Google (sin contraseña) → `401`.
- Login exitoso: actualiza `last_login_at`.
- Registro: email duplicado → `409`; siempre se crea con rol `STUDENT`, privilegio `BASIC`, estado `ACTIVE`.
- Estado: `DELETED` es un **borrado lógico** y es final (no se puede reactivar → `409`). Pedir el mismo estado que ya tiene devuelve `200` sin cambios.
- `DELETE /users/me` elimina definitivamente la cuenta, notas, inscripciones e intentos. Los logs de seguridad se conservan anonimizados.
- Recuperación: token aleatorio de un solo uso, expira en 30 minutos y en la base solo se guarda su hash.
- El `token` del login es un **JWT** (ver la sección de seguridad).

## 🔐 Seguridad con JWT

Toda la API pide `Authorization: Bearer <token>`, menos estas rutas:

| Acceso | Rutas |
|---|---|
| Pública | `POST /auth/login`, `POST /auth/register`, `POST /auth/forgot-password`, `POST /auth/reset-password`, `/h2-console` (solo en desarrollo) |
| Solo `ADMIN` | `PATCH /users/{id}/status`, `GET /security-logs/**` |
| Cualquier usuario con sesión | todo lo demás |

- **Token:** JWT firmado con HS256. Lleva `sub` (id del usuario), `email`, `role`, `iss` (`apunteia-backend`), `iat` y `exp`. Dura `JWT_EXPIRATION_MINUTES` (120 por defecto).
- **Secreto:** se toma de la variable `JWT_SECRET` (mínimo 32 caracteres). El valor por defecto de `application.yml` es solo para desarrollo; en la nube hay que definir uno propio (`openssl rand -base64 48`).
- **Errores**, con el mismo `ErrorResponse` del resto de la API:
    - `401`: falta el token, no tiene el prefijo `Bearer`, está mal formado, la firma fue alterada, ya venció, o la cuenta ya no está `ACTIVE` (un usuario bloqueado pierde el acceso de inmediato, aunque su token no haya vencido).
    - `403`: el token es válido, pero el rol no tiene permiso para esa ruta.
- **En un controlador**, el usuario del token se recibe con `@AuthenticationPrincipal user: AuthenticatedUser` (`user.id`, `user.email`, `user.role`).
- **Clases:** `SecurityConfig` (reglas), `JwtService` (genera y valida), `JwtAuthenticationFilter` (lee el header), `RestAuthenticationEntryPoint` y `RestAccessDeniedHandler` (respuestas 401 y 403).
- **Postman:** la colección manda `Bearer {{token}}` en todas las peticiones; las rutas solo-ADMIN usan `{{adminToken}}`. La carpeta **Seguridad JWT (Fernán)** va de primera porque guarda los dos tokens.

## Cursos, Inscripciones y Auditoría

El módulo del issue #22 usa Controllers para recibir HTTP y validar DTOs, Services para
aplicar reglas de negocio y transacciones, y Repositories para acceder a datos.
Los Mappers convierten las entidades en DTOs de respuesta, sin exponer entidades JPA.

### Cursos

| Método | Ruta | Entrada | Respuestas |
|---|---|---|---|
| `GET` | `/courses` | — | `200` lista de `CourseResponse` |
| `GET` | `/courses/{id}` | ID del curso | `200` `CourseResponse` · `400` · `404` |
| `POST` | `/courses` | `CreateCourseRequest`: `code`, `name`, `description` opcional | `201` `CourseResponse` + `Location` · `400` · `409` · `415` |
| `PUT` | `/courses/{id}` | `UpdateCourseRequest`: `code`, `name`, `description` opcional | `200` `CourseResponse` · `400` · `404` · `409` · `415` |
| `DELETE` | `/courses/{id}` | ID del curso | `204` · `400` · `404` · `409` |

### Inscripciones

| Método | Ruta | Entrada | Respuestas |
|---|---|---|---|
| `GET` | `/enrollments` | Filtros opcionales `userId`, `courseId` | `200` lista de `EnrollmentResponse` · `400` |
| `GET` | `/enrollments/{id}` | ID de la inscripción | `200` `EnrollmentResponse` · `400` · `404` |
| `POST` | `/enrollments` | `CreateEnrollmentRequest`: `userId`, `courseId` obligatorios y positivos | `201` `EnrollmentResponse` + `Location` · `400` · `404` · `409` · `415` |
| `DELETE` | `/enrollments/{id}` | ID de la inscripción | `204` · `400` · `404` |

Filtros existentes, que pueden combinarse:

- `GET /enrollments?userId={id}`
- `GET /enrollments?courseId={id}`
- `GET /enrollments?userId={id}&courseId={id}`

### Aliases solicitados por el issue

Las rutas originales se conservan. Estos aliases delegan al mismo `EnrollmentService`:

| Método | Ruta | Entrada | Respuestas |
|---|---|---|---|
| `POST` | `/courses/{id}/enrollments` | `EnrollUserRequest`: `{ "userId": 1 }`; el curso se toma de la ruta | `201` `EnrollmentResponse` + `Location` · `400` · `404` · `409` · `415` |
| `GET` | `/users/{id}/enrollments` | Usuario indicado en la ruta | `200` lista de `EnrollmentResponse` · `400` |

`EnrollUserRequest.userId` es obligatorio y positivo. Las consultas de inscripciones
devuelven `200` con una lista vacía cuando no hay coincidencias, incluso para un usuario inexistente.

### Auditoría

| Método | Ruta | Entrada | Respuestas |
|---|---|---|---|
| `GET` | `/security-logs` | Filtros opcionales `userId`, `eventType`, `anonymousOnly` | `200` lista de `SecurityLogResponse` · `400` |
| `GET` | `/security-logs/{id}` | ID del evento | `200` `SecurityLogResponse` · `400` · `404` |

Filtros existentes:

- `GET /security-logs?userId={id}`
- `GET /security-logs?eventType={tipo}`
- `GET /security-logs?anonymousOnly=true`

Los filtros se pueden combinar; `anonymousOnly=true` selecciona únicamente eventos sin usuario.
Los resultados se devuelven del más reciente al más antiguo, con desempate por ID descendente.
La API de Security Logs es de solo lectura: no permite crear, modificar ni eliminar eventos por HTTP.
Los métodos HTTP no admitidos devuelven `405 Method Not Allowed`.

### Respuestas y reglas de negocio

- `200 OK`: consulta o actualización correcta; las listas pueden estar vacías.
- `201 Created`: creación correcta. `POST /courses` devuelve `Location: /courses/{id}`;
  ambos POST de inscripción devuelven `Location: /enrollments/{id}`. La URL puede incluir el host.
- `204 No Content`: eliminación correcta, sin cuerpo de respuesta.
- `400 Bad Request`: DTO inválido, JSON mal formado o parámetros con formato incorrecto.
- `404 Not Found`: recurso solicitado inexistente o usuario/curso inexistente al inscribir.
- `405 Method Not Allowed`: método HTTP no admitido para la ruta.
- `409 Conflict`: código de curso o inscripción duplicados, o eliminación de un curso con inscripciones.
- `415 Unsupported Media Type`: tipo de contenido no admitido; enviar `application/json` en las operaciones con body.

El código del curso debe ser único y se normaliza quitando espacios en los extremos.
No se puede inscribir a un usuario inexistente ni en un curso inexistente, y un usuario
no puede inscribirse dos veces en el mismo curso. No se puede eliminar un curso con
inscripciones activas: las relaciones existentes impiden el borrado y la API devuelve `409`.

Los Security Logs se generan internamente por `LOGIN`, `LOGIN_FAILED`, `USER_REGISTERED`
y `USER_STATUS_CHANGED`. `record` participa en la transacción actual para guardar los
eventos exitosos junto con su operación y asociados al usuario correspondiente.
`recordIndependent` usa `REQUIRES_NEW`: `LOGIN_FAILED` se guarda en una transacción
independiente y sobrevive al rollback del login. Un log puede ser anónimo cuando no existe
un usuario identificable. El tipo de evento es obligatorio y los detalles se limitan a 1000 caracteres.

### Verificación del módulo

Los DTOs son `CreateCourseRequest`, `UpdateCourseRequest`, `CourseResponse`,
`CreateEnrollmentRequest`, `EnrollUserRequest`, `EnrollmentResponse` y `SecurityLogResponse`.
Los Mappers son `CourseMapper`, `EnrollmentMapper` y `SecurityLogMapper`; los servicios
son `CourseService`, `EnrollmentService` y `SecurityLogService`.

La carpeta **Cursos, Inscripciones y Logs (Alice)** de la colección Postman contiene
**40 requests**. Ejecutarla completa y en orden: conserva las 29 originales y agrega
comprobaciones de logs reales, aliases y limpieza. Usa IDs dinámicos y elimina los cursos
e inscripciones temporales; los eventos de auditoría se conservan.

Para verificar el backend desde PowerShell:

```powershell
.\gradlew.bat clean test --console=plain
docker compose ps
```

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

Para que recuperación envíe correos, configura `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS` y `MAIL_FROM`. Configura `PASSWORD_RESET_URL` con el deep link de la app que abre la pantalla de nueva contraseña (por defecto `apunteia://reset-password`).

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

`LoginResponse` respeta el contrato que ya usa la app (`{ token, userName }`). Hay que cambiar la URL base de Retrofit y mandar el token en cada petición con un interceptor (`Authorization: Bearer <token>`):

- Emulador de Android: `http://10.0.2.2:8080/`
- Celular físico en la misma red: `http://<IP-de-la-compu>:8080/`

---
