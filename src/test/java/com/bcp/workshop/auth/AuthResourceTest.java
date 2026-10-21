package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class AuthResourceTest {
    private static final String TEST_SECRET = "synthetic-test-key-not-for-deployment-0001";

    @Test
    void returnsSignedTokenWithExpectedClaimsAndLifetime() {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("expiresIn", is(3600))
                .extract().path("token");

        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(TEST_SECRET)).build();
        var decoded = verifier.verify(token);
        assertEquals("student", decoded.getSubject());
        assertEquals("STUDENT", decoded.getClaim("role").asString());
        assertEquals(3600, decoded.getExpiresAt().toInstant().getEpochSecond()
                - decoded.getIssuedAt().toInstant().getEpochSecond());

        String[] parts = token.split("\\.");
        String modifiedSignature = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        assertThrows(JWTVerificationException.class,
                () -> verifier.verify(parts[0] + "." + parts[1] + "." + modifiedSignature));

        String expiredToken = JWT.create()
                .withSubject("student")
                .withClaim("role", "STUDENT")
                .withIssuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .withExpiresAt(Date.from(Instant.now().minusSeconds(3600)))
                .sign(Algorithm.HMAC256(TEST_SECRET));
        assertThrows(TokenExpiredException.class, () -> verifier.verify(expiredToken));
    }

    @Test
    void characterizesSuccessfulResponseShape() {
        JsonPath json = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertEquals(Set.of("token", "expiresIn", "user", "debug"), json.getMap("").keySet());
        assertEquals(Set.of("username", "displayName", "role"), json.getMap("user").keySet());
        assertEquals("student", json.getString("user.username"));
        assertEquals("Workshop Student", json.getString("user.displayName"));
        assertEquals("STUDENT", json.getString("user.role"));
        assertEquals(3600, json.getInt("expiresIn"));
        assertEquals("authenticated-with-legacy-flow", json.getString("debug"));
    }

    @Test
    void characterizesOmittedClientCurrentlySucceeds() {
        // Baseline only: task 2.1 replaces this expectation with 400.
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue());
    }

    @Test
    void rejectedRequestsIssueNoToken() {
        assertRejectedWithoutToken("null", 400);
        assertRejectedWithoutToken("{\"username\":null,\"password\":\"Workshop123!\"}", 400);
        assertRejectedWithoutToken("{\"username\":\" \",\"password\":\"Workshop123!\"}", 400);
        assertRejectedWithoutToken("{\"username\":\"student\",\"password\":null}", 400);
        assertRejectedWithoutToken("{\"username\":\"student\",\"password\":\" \"}", 400);
        assertRejectedWithoutToken("{\"username\":\"unknown-user\",\"password\":\"wrong-password\"}", 401);
        assertRejectedWithoutToken("{\"username\":\"student\",\"password\":\"wrong-password\"}", 401);
    }

    private static void assertRejectedWithoutToken(String body, int expectedStatus) {
        given()
                .contentType("application/json")
                .body(body)
                .when().post("/auth/login")
                .then()
                .statusCode(expectedStatus)
                .body("token", nullValue());
    }

    @Test
    void rejectsMissingOrShortSigningKeys() {
        assertThrows(IllegalStateException.class, () -> LegacyTokenUtil.create("student", "STUDENT", null));
        assertThrows(IllegalStateException.class, () -> LegacyTokenUtil.create("student", "STUDENT", "too-short"));
    }

    @Test
    void rejectsNullLoginRequest() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsMissingOrBlankUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":null,\"password\":\"test-password\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400);

        given()
                .contentType("application/json")
                .body("{\"username\":\" \",\"password\":\"test-password\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsMissingOrBlankPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":null}")
                .when().post("/auth/login")
                .then()
                .statusCode(400);

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\" \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"unknown-user\",\"password\":\"wrong-password\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void returnsServerErrorWhenSigningKeyIsInvalid() {
        AuthResource resource = new AuthResource();
        resource.database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                return new UserRecord(1, username, "Workshop Student", "STUDENT");
            }
        };
        resource.tokenSigningKey = "too-short";

        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "test-password";

        assertEquals(500, resource.login(request).getStatus());
    }
}
