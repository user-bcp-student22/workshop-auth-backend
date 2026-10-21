# Proposal

## Why

Issue #10 (HU-01) pide que el token de login permita comprobar identidad, cliente y vigencia, y que su vida útil y manejo del secreto sean verificables. El contrato del Día 1 acepta `client` pero no lo usa; vincularlo al token y exigirlo evita emitir credenciales sin el contexto de cliente que la historia requiere.

## What Changes

- **BREAKING**: el valor de `token` dejará de seguir la representación Base64 legacy descrita por el contrato del Día 1 y pasará a ser un token firmado con claims. El campo JSON seguirá siendo una cadena; los consumidores no deberán depender del formato legacy.
- **BREAKING**: `client`, opcional en el contrato del Día 1, será obligatorio y no podrá ser nulo, vacío o quedar en blanco. Su ausencia o invalidez se tratará como una entrada inválida.
- El token emitido por un login válido incluirá los claims `sub`, `client`, `iat` y `exp`; `sub` identificará al usuario autenticado y `client` corresponderá al valor validado de la solicitud.
- `expiresIn` seguirá expresado en segundos y conservará el valor documentado de 3600; deberá ser coherente con la expiración del token dentro de una tolerancia documentada.
- El secreto de firma no tendrá un valor por defecto fuera del entorno de laboratorio y ni el secreto ni el token se expondrán en logs.
- Se conservarán el endpoint `POST /auth/login`, la autenticación sin credenciales previas, la respuesta 200 y sus campos documentados `token`, `expiresIn`, `user` y `debug`, además de los errores 400 por campos requeridos existentes y 401 por credenciales inválidas.
- La elección entre conservar HMAC o adoptar una biblioteca JWT queda como decisión técnica pendiente; esta propuesta no fija algoritmo, biblioteca ni formato de implementación.

## Out of Scope

- Cambios en `GET /q/health`, CORS, manejo de JSON malformado o validaciones de `username` y `password` que no sean necesarias para conservar el contrato existente.
- Redefinir el formato de respuestas 500 o sus detalles de excepción; el requisito de seguridad de esta HU se limita a no registrar tokens ni secretos.
- Elegir algoritmo, biblioteca, formato de configuración interno o pasos de implementación para la firma del token.

## Capabilities

### New Capabilities
- `auth-login-token`: comportamiento observable del token y del contrato de `POST /auth/login` asociado a claims, cliente, expiración y protección de datos sensibles.

### Modified Capabilities
- Ninguna. El repositorio no tiene specs existentes; la capacidad se introduce como una spec nueva.

## Impact

- API documentada en `docs/ENDPOINTS.md`, principalmente el request y response de `POST /auth/login`.
- Emisión del token, configuración del secreto, registro de eventos de autenticación y pruebas de aceptación del login.
- El cambio de `client` opcional a obligatorio es incompatible con clientes que omitan ese campo. La forma de firmar el token y la selección de biblioteca quedan fuera de esta propuesta y requieren decisión técnica posterior.