# Contrato de Endpoints

## Endpoint: POST /auth/login

### 1) Que hace

Autentica a un usuario de laboratorio y devuelve un token junto con datos basicos del usuario autenticado.

### 2) Autenticacion requerida

No requiere autenticacion previa.

Header esperado:

- Content-Type: application/json

### 3) Request

Campos del body JSON:

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| username | string | Si | Usuario de login. Si viene nulo, vacio o en blanco, responde 400. |
| password | string | Si | Contrasena del usuario. Si viene nula, vacia o en blanco, responde 400. |
| client | string | Si | Cliente que solicita el login. Si viene nulo, vacio o en blanco, responde 400 y no emite token. Se incluye en el claim `client` del token. |

Ejemplo JSON real y completo:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

### 4) Response exitosa

Codigo de estado: 200 OK

Campos del body JSON:

| campo | tipo | descripcion |
|---|---|---|
| token | string | JWT firmado con HMAC256 (libreria `java-jwt`). Claims: `sub` (username), `client`, `role`, `iat` y `exp`. |
| expiresIn | integer | Tiempo de vida del token en segundos (valor fijo: 3600). Coincide con `exp - iat` del token, con una tolerancia documentada de 2 segundos al momento de la respuesta. |
| user | object | Datos del usuario autenticado. |
| user.username | string | Username persistido del usuario autenticado. |
| user.displayName | string | Nombre de despliegue del usuario (escapado en HTML). |
| user.role | string | Rol persistido del usuario (ejemplo: STUDENT o TRAINER). |
| debug | string | Marca de flujo legacy actual. |

Ejemplo JSON real y completo:

```json
{
  "token": "<jwt firmado HMAC256>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

### 5) Respuestas de error posibles

Codigo: 400 Bad Request

Cuando ocurre:

- Request nulo.
- username nulo, vacio o en blanco.
- password nula, vacia o en blanco.
- client nulo, vacio o en blanco.

Ejemplos de cuerpo:

```json
{"message":"Request is null"}
```

```json
{"message":"Username is required"}
```

```json
{"message":"Password is required"}
```

```json
{"message":"Client is required"}
```

Codigo: 401 Unauthorized

Cuando ocurre:

- Credenciales invalidas (usuario no encontrado o password no coincide).

Ejemplo de cuerpo:

```json
{"message":"Invalid user student or password"}
```

Codigo: 500 Internal Server Error

Cuando ocurre:

- Se lanza una excepcion no controlada durante el flujo de login.

Ejemplo de cuerpo:

```json
{
  "message": "Unknown error",
  "exception": "java.lang.RuntimeException"
}
```

Pregunta abierta:

- El comportamiento exacto para JSON malformado o Content-Type distinto de application/json depende del runtime REST de Quarkus y no pudo verificarse por ejecucion en este entorno.

### 6) Notas de seguridad relevantes

- El token es un JWT firmado con HMAC256 usando la clave `AUTH_TOKEN_SECRET` (minimo 32 bytes UTF-8). Sin esa variable, el servicio no debe emitir tokens firmados. El secreto de prueba solo existe en el perfil `%test`.
- El token no se registra en logs ni se expone fuera de la respuesta de login.
- Decision HU-01: se conserva HMAC256 con la libreria `java-jwt` ya existente; no se adopta otra libreria JWT ni firma asimetrica en este cambio.
- La respuesta de error 500 expone detalles internos (mensaje y clase de excepcion).
- CORS esta habilitado de forma permisiva en configuracion.

## Endpoint: GET /q/health

### 1) Que hace

Expone el estado de salud del servicio para monitoreo y orquestacion.

### 2) Autenticacion requerida

No requiere autenticacion previa.

Header esperado:

- No requiere header de autenticacion.

### 3) Request

Este endpoint no recibe body.

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| Sin campos | N/A | N/A | Request GET sin cuerpo. |

Ejemplo de request completo:

```json
{}
```

### 4) Response exitosa

Codigo de estado esperado: 200 OK

Campos del body JSON (segun SmallRye Health):

| campo | tipo | descripcion |
|---|---|---|
| status | string | Estado global de salud del servicio (por ejemplo UP). |
| checks | array | Lista de checks reportados por el runtime. |

Ejemplo JSON real y completo:

```json
{
  "status": "UP",
  "checks": []
}
```

Pregunta abierta:

- El shape exacto de checks depende de los health checks activos en runtime y no pudo verificarse por ejecucion en este entorno.

### 5) Respuestas de error posibles

Codigo potencial: 503 Service Unavailable

Cuando ocurre:

- Al menos un health check reporta estado no saludable.

Ejemplo de cuerpo:

```json
{
  "status": "DOWN",
  "checks": []
}
```

Pregunta abierta:

- No se pudo verificar por ejecucion local el contenido exacto del body para estados DOWN.

### 6) Notas de seguridad relevantes

- Este endpoint suele exponerse para observabilidad; conviene limitar su exposicion por red/perimetro segun el entorno.
- No se observaron controles de autenticacion especificos en este codigo para /q/health.

## Documentos y fuentes consultadas

- README.md
- REGLAS.md
- docs/GOOD_PRACTICES.md
- HALLAZGOS.md
- pom.xml
- src/main/resources/application.properties
- src/main/java/com/bcp/workshop/auth/AuthResource.java
- src/main/java/com/bcp/workshop/auth/LoginRequest.java
- src/main/java/com/bcp/workshop/auth/LegacyDatabase.java
- src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java