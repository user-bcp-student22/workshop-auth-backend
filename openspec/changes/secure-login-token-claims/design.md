# Design

## Context

See `proposal.md` for motivation and `specs/auth-login-token/spec.md` for the normative behavior.

The current `POST /auth/login` implementation is in `AuthResource`: it validates `username` and `password`, accepts but does not use `client`, and returns `token`, `expiresIn`, `user`, and `debug`. `LegacyTokenUtil` already creates a signed token with the repository's existing `java-jwt` dependency and HMAC256, with `sub`, `role`, `iat`, and `exp`. The configured signing key comes from `AUTH_TOKEN_SECRET`; tests use a test-profile value. Existing tests cover signature verification, subject/role, a 3600-second issued lifetime, expiry verification, and the current username/password error outcomes.

There is a source-of-truth mismatch to resolve while implementing: `docs/ENDPOINTS.md` describes the token as Base64 concatenated data, while runtime code and tests already use a signed JWT. This design preserves the runtime signing approach and requires the contract documentation to describe the externally observable signed token rather than the stale Base64 description.

## Goals / Non-Goals

**Goals:**
- Implement the requirements in `specs/auth-login-token/spec.md` within the existing login flow.
- Keep the existing token-signing dependency and algorithm; add only the claims and request validation needed by the spec.
- Preserve existing response and credential-error contracts while making the documented compatibility breaks explicit.
- Make the lifetime comparison deterministic and keep the signing secret and token out of logs.

**Non-Goals:**
- Add an authentication endpoint, token refresh/revocation, an authorization framework, or a client registry/allowlist.
- Replace the database, redesign login, change CORS, or redefine the 500 response contract.
- Rotate the signing secret, change the signing algorithm, or introduce another JWT library as part of this change.

## Protected Contracts

- `POST /auth/login` remains unauthenticated and continues to return 200 with `token`, `expiresIn`, `user`, and `debug` for successful authentication. The `user` object retains `username`, `displayName`, and `role`.
- Null requests and missing/blank `username` or `password` retain their 400 outcomes; invalid credentials retain 401. These conditions must not issue a token.
- The token remains a string in the response. Its serialized value is signed and retains the existing `role` claim while adding the required claims; consumers must not depend on the stale Base64-concatenation description in the Day 1 document.
- The intentional request-contract change is that `client` is required and nonblank. Validation is limited to presence and non-whitespace content because no allowed-client registry or format is defined. Preserve the supplied value for the `client` claim; do not case-fold or otherwise normalize it.
- `sub` identifies the authenticated username. `iat` and `exp` use the token's issuance and expiry instants. The nominal lifetime remains 3600 seconds. Define `now` for the `expiresIn` comparison as server time immediately before response serialization, and document a maximum absolute difference of 2 seconds to account for whole-second claim precision and response construction.
- The current 500 response details are not changed by this design; they remain outside the new requirements.

## Components Affected

- `AuthResource`: validate the new required request field and preserve the response/error contract.
- `LegacyTokenUtil`: include the validated client claim while keeping the existing signature and lifetime behavior.
- `application.properties` and deployment configuration: continue sourcing the key from `AUTH_TOKEN_SECRET`; ensure production-like profiles do not acquire a fallback value. The test-only value remains isolated to the test profile.
- `AuthResourceTest`: characterize the existing contract, then cover the new request, claim, lifetime, expiry, secret, and log requirements.
- `docs/ENDPOINTS.md`: reconcile the request and token descriptions with the approved contract and preserve the response fields/statuses.

## Decisions

1. **Retain HMAC256 with the existing `java-jwt` dependency.** The repository already uses both: HMAC256 is the signing algorithm and `java-jwt` provides the JWT representation. Reusing them avoids a new dependency and avoids an unrelated cryptographic migration. The alternative of introducing another JWT/JWS library or changing to asymmetric signing is deferred; it increases dependency and key-management scope without being required by HU-01. Record this choice in project documentation as required by the issue.

2. **Treat a valid client as a nonblank string and preserve it verbatim.** The approved spec requires presence and non-whitespace content but defines no registry, syntax, or case policy. Reject null, empty, and whitespace-only values; do not invent an allowlist or silently rewrite a caller's value. A future allowlist would require a separate product decision and spec change.

3. **Keep the nominal 3600-second lifetime and use a two-second comparison tolerance.** The token claims have whole-second precision and `expiresIn` is an integer. Compare `expiresIn` with `exp - now` using server time immediately before response serialization. A larger tolerance or a client-side measurement would hide clock/latency errors; a zero tolerance would make integer rounding observable as a false mismatch.

4. **Do not introduce a logging path for credential artifacts.** The login path should not log token values or key material. Alternatives that redact after logging are rejected because they still risk exposing raw values in intermediate sinks.

## Incremental Strategy

1. Add characterization coverage before changing the critical login behavior: record the current success response shape and user fields, current `debug` value, 3600-second lifetime, signed-token verification, and current 400/401 outcomes. Also record that the current implementation accepts an omitted `client`; mark that assertion as the intentional behavior to replace, not a contract to preserve.
2. Add acceptance coverage for required/nonblank `client`, `client` claim equality, required claims, signature verification, expired-token rejection, and the `expiresIn` tolerance. Keep these tests at the HTTP boundary where the behavior is observable; test token claims with the existing verifier dependency.
3. Cover secret handling and log redaction using a synthetic sentinel secret/token. Verify that non-laboratory configuration without an explicit secret cannot produce a successful signed token, and that captured authentication logs contain neither sentinel.
4. Update `docs/ENDPOINTS.md` to document the accepted request, intentional `client` incompatibility, signed-token claims, lifetime tolerance, secret configuration, and unchanged response/error contract. Run the focused authentication tests before broader verification.

## Characterization Tests Before Critical Changes

- Capture the current successful HTTP response keys and nested `user` keys, plus `debug` and `expiresIn`, before changing request validation or token generation.
- Preserve tests for null request, missing/blank username and password, and invalid credentials; assert their status and that no token is returned.
- Characterize the existing signature, `sub`, `role`, `iat`, `exp`, 3600-second nominal lifetime, and expiry rejection. The runtime already emits a signed JWT even though the Day 1 document says Base64; keep this mismatch visible until the documentation is corrected.
- Add a baseline case demonstrating that omitted `client` currently succeeds, then replace its expected result with 400 in the acceptance suite when the breaking behavior is implemented.
- Use fixed/synthetic secret values and bounded time comparisons; do not assert exact wall-clock timestamps or put real credentials in test output.

## Risks / Trade-offs

- [Client becomes mandatory] Existing callers that omit `client` will receive 400 → update callers before rollout and monitor 400 rates; rollback to the prior application version remains possible.
- [Documentation/runtime mismatch] Consumers may have relied on the Base64 description or may already consume the signed JWT → update documentation and verify representative consumers against the actual signed-token contract before release.
- [Clock and response timing] Lifetime assertions can become flaky if measured at a different instant → define the comparison at server-side issuance and use the documented 2-second bound.
- [Missing production secret] Removing any non-laboratory fallback can prevent login from issuing tokens → verify `AUTH_TOKEN_SECRET` is provisioned before deployment; never compensate with an embedded fallback.
- [Log capture gaps] A test logger may miss framework or deployment sinks → keep application code free of token/key logging and validate the configured production log path during release verification.
- [Rollback compatibility] The previous application version may accept requests without `client` and issue tokens without the new claim → do not make downstream behavior depend on `client` until the rollout is stable; keep the signing key unchanged so tokens already issued remain verifiable until their normal expiry.

## Migration / Rollback

Deploy this as an application and documentation release after clients have begun sending a nonblank `client`. Do not rotate the signing key in the same release. There is no data migration.

If login failures or compatibility problems require rollback, restore the previous application version and retain the existing signing key. The previous version can accept callers that omit `client`, but it will not emit the new claim; consumers must therefore tolerate that during the rollback window and must not assume the claim is universally present until the rollout is confirmed. Existing tokens remain subject to their original expiry. Reapply the release after correcting the cause; do not introduce a second token format or a temporary default secret as a rollback shortcut.

## Open Questions

None that change this approach or the approved scenarios. The signing choice, client validation boundary, and lifetime tolerance are decided above; any future change to those decisions requires updating the spec.