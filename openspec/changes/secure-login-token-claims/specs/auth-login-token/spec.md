# Spec Delta

## Purpose

Define el contrato observable del token emitido por `POST /auth/login`, incluidos los claims mínimos, el contexto de cliente, la expiración y la protección del secreto y del token.

## ADDED Requirements

### Requirement: Successful login issues a signed token with required claims
The service MUST issue a verifiably signed token containing at least `sub`, `client`, `iat`, and `exp`. The `sub` claim MUST identify the authenticated username, and `client` MUST match the validated request value.

#### Scenario: Valid login returns the required claims
- **WHEN** a request to `POST /auth/login` supplies valid credentials and a nonblank client and authentication succeeds
- **THEN** the response is `200` and its token contains `sub`, `client`, `iat`, and `exp`, with `sub` identifying the authenticated user and `client` matching the request

### Requirement: Client is required and nonblank for login
The login request MUST include a non-null, non-empty, non-whitespace `client`; this explicitly changes the optional, unused field documented on Day 1.

#### Scenario: Client is omitted or null
- **WHEN** a login request otherwise has valid credentials but omits `client` or supplies it as null
- **THEN** the service rejects the request with `400 Bad Request` and issues no token

#### Scenario: Client is blank
- **WHEN** a login request otherwise has valid credentials but supplies an empty or whitespace-only `client`
- **THEN** the service rejects the request with `400 Bad Request` and issues no token

### Requirement: Successful login preserves the documented response shape
For valid credentials and a valid client, the unauthenticated `POST /auth/login` endpoint MUST return `200` with the documented `token`, `expiresIn`, `user`, and `debug` fields; `user` MUST retain `username`, `displayName`, and `role`.

#### Scenario: Successful login response
- **WHEN** a client submits valid credentials and a valid `client` without prior authentication
- **THEN** the response is `200` and contains all documented top-level and user fields

### Requirement: Existing credential validation outcomes are preserved
The service MUST preserve the Day 1 outcomes for null requests, missing or blank usernames or passwords, and invalid credentials; only the newly required `client` adds another invalid-input condition.

#### Scenario: Missing required credential input
- **WHEN** the request is null or `username` or `password` is missing, null, empty, or blank
- **THEN** the service returns `400 Bad Request` with the documented validation message and issues no token

#### Scenario: Invalid credentials
- **WHEN** a request with a valid `client` contains credentials that do not authenticate a user
- **THEN** the service returns `401 Unauthorized` and issues no token

### Requirement: Token lifetime agrees with expiresIn
The token lifetime MUST retain the documented nominal value of 3600 seconds, and `expiresIn` MUST be expressed in seconds and differ from `exp - now` by no more than a documented tolerance.

#### Scenario: Successful login reports a coherent lifetime
- **WHEN** a successful response's token expiry and response time are inspected
- **THEN** `expiresIn` is 3600 seconds and its difference from `exp - now` is within the documented tolerance

### Requirement: Expired tokens are rejected
A token MUST NOT be accepted as valid at or after its `exp` claim.

#### Scenario: Token is checked after expiry
- **WHEN** an expired token is presented for validation at or after `exp`
- **THEN** validation rejects the token as expired

### Requirement: Signing secret has no non-laboratory default
Outside laboratory use, token signing MUST use an explicitly configured `AUTH_TOKEN_SECRET` and MUST NOT fall back to a default or embedded secret.

#### Scenario: Signing secret is absent outside the laboratory
- **WHEN** the service handles a login outside laboratory use without `AUTH_TOKEN_SECRET` configured
- **THEN** it does not return a successfully signed token using a fallback secret

### Requirement: Tokens and signing secrets are excluded from logs
Authentication logs MUST NOT contain issued tokens or the signing secret.

#### Scenario: Successful and rejected logins are recorded
- **WHEN** the service records logs for successful and failed login attempts
- **THEN** neither the token nor the signing secret appears in those logs

### Requirement: Token signing approach is documented
The project MUST document whether token signing retains HMAC or adopts a JWT library without prescribing that choice in this change.

#### Scenario: Signing decision is recorded
- **WHEN** the HU-01 change is considered complete
- **THEN** project documentation records the decision to retain HMAC or adopt a JWT library