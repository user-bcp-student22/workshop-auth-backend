# Tasks

## 1. Characterize the existing login contract

- [x] 1.1 Extend the HTTP characterization tests to assert the current successful response status and shape (`token`, `expiresIn`, `user`, `debug`), nested user fields, `debug` value, and nominal 3600-second lifetime; record that omitted `client` currently succeeds. Verify with `mvn -Dtest=AuthResourceTest test` before changing login behavior.
- [x] 1.2 Characterize the existing 400 outcomes for null request and missing/blank username or password, the 401 outcome for invalid credentials, and signed-token verification/expiry behavior; assert rejected requests return no token. Verify with `mvn -Dtest=AuthResourceTest test`.

## 2. Require and propagate the client

- [ ] 2.1 Add HTTP contract tests for omitted, null, empty, and whitespace-only `client`, expecting 400 and no token; verify these tests fail against the characterized baseline for the intended reason.
- [ ] 2.2 Validate that `client` is present and nonblank before issuing a token, preserving the supplied value without normalization; verify all new 400 cases and the characterized login/error tests pass with `mvn -Dtest=AuthResourceTest test`.

## 3. Complete token claims and lifetime behavior

- [ ] 3.1 Add a token assertion for the required `client` claim and the preserved `sub`, `role`, `iat`, and `exp` claims, including signature verification; verify the new assertion fails before the claim is added.
- [ ] 3.2 Include the validated client in the signed token while retaining the current signing approach and role claim; verify claim values, signature, nominal lifetime, and expired-token rejection with `mvn -Dtest=AuthResourceTest test`.
- [ ] 3.3 Assert that successful responses report `expiresIn` as 3600 seconds and that it differs from `exp - now` by no more than the documented two-second tolerance at response time; verify with the focused authentication tests.

## 4. Protect signing configuration and logs

- [ ] 4.1 Add a configuration-level test proving non-laboratory execution without `AUTH_TOKEN_SECRET` cannot return a successfully signed token, while the test-only secret remains isolated to the test profile; verify with the focused authentication tests and profile configuration.
- [ ] 4.2 Add a log-capture test using synthetic sentinel values and ensure successful and rejected login paths emit neither the token nor signing secret; verify the captured authentication logs contain neither sentinel.

## 5. Align documentation and verify integration

- [ ] 5.1 Update `docs/ENDPOINTS.md` to require nonblank `client`, describe the signed-token claims and 3600-second lifetime with two-second tolerance, document `AUTH_TOKEN_SECRET`, preserve response/error fields, replace the stale Base64 description, and record the retained HMAC256/`java-jwt` decision; verify the document against `auth-login-token/spec.md` and the HTTP contract tests.
- [ ] 5.2 Run the complete Maven test suite and `openspec validate secure-login-token-claims --type change --strict`; confirm both succeed and that no behavior outside the approved login-token scope changed.