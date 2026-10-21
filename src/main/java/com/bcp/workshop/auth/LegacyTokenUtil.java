package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public final class LegacyTokenUtil {
    private static final long TOKEN_TTL_SECONDS = 3600;
    private static final int MINIMUM_KEY_LENGTH_BYTES = 32;

    private LegacyTokenUtil() {}

    public static String create(String username, String role, String client, String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_KEY_LENGTH_BYTES) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must be at least 32 UTF-8 bytes");
        }

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return JWT.create()
                .withSubject(username)
                .withClaim("role", role)
                .withClaim("client", client)
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(issuedAt.plusSeconds(TOKEN_TTL_SECONDS)))
                .sign(Algorithm.HMAC256(secret));
    }
}
