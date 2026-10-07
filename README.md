# UsersService

Servicio de usuarios con autenticación de Google (OAuth 2.0 / Google Sign-In) y JWT propio.
Spring Boot 4.1, Java 21, Maven.

## 1. Credenciales OAuth en Google Cloud Console

1. Entra a <https://console.cloud.google.com/> y crea (o selecciona) un proyecto.
2. **APIs & Services > OAuth consent screen**: configura la pantalla de consentimiento
   (tipo *External*, nombre de la app, correo de soporte) y agrega tu cuenta como *test user*.
3. **APIs & Services > Credentials > Create credentials > OAuth client ID**.
4. Application type: **Web application**.
5. **Authorized redirect URIs**: `http://localhost:8080/api/auth/google/callback`
   (debe coincidir exactamente con `GOOGLE_REDIRECT_URI`).
6. Si el frontend usa Google Sign-In, agrega su origen en **Authorized JavaScript origins**
   (ej. `http://localhost:3000`).
7. Copia el **Client ID** y el **Client secret**.

## 2. Variables de entorno

Copia `.env.example` a `.env` y completa los valores. `.env` está en `.gitignore` y la
aplicación lo carga automáticamente si existe; las variables de entorno reales tienen prioridad.

| Variable | Descripción | Por defecto |
|---|---|---|
| `GOOGLE_CLIENT_ID` | Client ID de OAuth | requerido |
| `GOOGLE_CLIENT_SECRET` | Client secret de OAuth | requerido |
| `GOOGLE_REDIRECT_URI` | URI de redirección autorizada | `http://localhost:8080/api/auth/google/callback` |
| `JWT_SECRET` | Clave HS256, mínimo 256 bits (32+ caracteres) | requerido |
| `JWT_EXPIRATION_MS` | Vigencia del JWT en milisegundos | `86400000` |
| `FRONTEND_URL` | Origen(es) permitidos por CORS, separados por coma | `http://localhost:3000` |

Generar un secreto: `openssl rand -base64 48`

Solo para el perfil `prod` (PostgreSQL): `DB_URL` (ej. `jdbc:postgresql://localhost:5432/users`),
`DB_USERNAME`, `DB_PASSWORD`.

## 3. Ejecutar

```bash
# Desarrollo: perfil dev (por defecto), H2 en memoria
./mvnw spring-boot:run

# Producción: PostgreSQL
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run

# Build y tests
./mvnw verify
```

En Windows usa `mvnw.cmd` en lugar de `./mvnw`.

## 4. Endpoints

Públicos: `/api/auth/google` y `/api/auth/google/**`. El resto requiere
`Authorization: Bearer <jwt>`.

### GET /api/auth/google/login

Devuelve la URL de autorización de Google. Con `?redirect=true` responde `302` hacia Google.
También deja una cookie `oauth_state` que se valida en el callback.

```bash
curl -c cookies.txt http://localhost:8080/api/auth/google/login
# {"authorizationUrl":"https://accounts.google.com/o/oauth2/auth?client_id=...&state=..."}
```

En el navegador abre directamente `http://localhost:8080/api/auth/google/login?redirect=true`.

### GET /api/auth/google/callback?code=...&state=...

Google redirige aquí después del consentimiento. Canjea el `code`, crea o actualiza el usuario
y devuelve el JWT. Normalmente lo invoca el navegador; con curl hay que reenviar la cookie:

```bash
curl -b cookies.txt "http://localhost:8080/api/auth/google/callback?code=CODE&state=STATE"
```

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInMs": 86400000,
  "user": {
    "id": "7c0e1f0e-3b0a-4a39-9d0c-2f1f2f6f3a11",
    "name": "Ada Lovelace",
    "email": "ada@example.com",
    "picture": "https://lh3.googleusercontent.com/...",
    "createdAt": "2026-10-07T19:00:00Z"
  }
}
```

### POST /api/auth/google

Para cuando el frontend obtiene el ID token con Google Sign-In. Misma respuesta que el callback.

```bash
curl -X POST http://localhost:8080/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"GOOGLE_ID_TOKEN"}'
```

### POST /api/auth/logout

Invalida el JWT (blacklist en memoria hasta su expiración).

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer JWT"
# {"message":"Logged out"}
```

## 5. Errores

Todas las respuestas de error tienen la misma forma:

```json
{
  "timestamp": "2026-10-07T19:06:02.517Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired Google ID token",
  "path": "/api/auth/google"
}
```

- `400` request mal formado (body inválido, parámetros faltantes, `state` de OAuth incorrecto)
- `401` token de Google o JWT inválido, expirado o revocado
- `404` usuario no encontrado
- `500` error inesperado

## 6. Notas

- La blacklist de logout vive en memoria: se pierde al reiniciar y no se comparte entre
  instancias. Para varias réplicas, muévela a Redis o a la base de datos.
- En `prod` el esquema se gestiona con `spring.jpa.hibernate.ddl-auto=update`
  (configurable con `DB_DDL_AUTO`). Para producción real conviene usar migraciones (Flyway).
