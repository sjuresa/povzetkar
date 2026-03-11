package com.povzetkar.quarkus.api.filter;

import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.IdempotentRequest;
import com.povzetkar.quarkus.model.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class IdempotencyFilterTest {

    @Path("/api/v1/test-idempotency")
    @ApplicationScoped
    public static class DummyIdempotentResource {
        
        @POST
        @Idempotent
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response processRequest(Map<String, Object> body) {
            return Response.ok(Map.of("result", "success", "echo", body)).build();
        }

        @POST
        @Path("/slow")
        @Idempotent
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response processSlowRequest(Map<String, Object> body) throws InterruptedException {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 1000) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                Thread.onSpinWait();
            }
            return Response.ok(Map.of("result", "slow_success")).build();
        }

        @POST
        @Path("/error")
        @Idempotent
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response processErrorRequest(Map<String, Object> body) {
            return Response.serverError().entity(Map.of("error", "failed")).build();
        }
    }

    User testUser;

    @BeforeEach
    @Transactional
    void setup() {
        testUser = UserBuilder.aUser()
                .withEmail("idemp@example.com")
                .withName("Idemp User")
                .withUsername("idempuser")
                .build();
        testUser.persistAndFlush();
    }

    @AfterEach
    @Transactional
    void teardown() {
        IdempotentRequest.deleteAll();
        User.deleteAll();
    }

    @Test
    @TestSecurity(user = "idempuser", roles = { "user" })
    void testSuccessfulIdempotentRequest() {
        String key = UUID.randomUUID().toString();
        String payload = "{\"test\":123}";

        // First request should succeed
        given()
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/api/v1/test-idempotency")
                .then()
                .statusCode(200)
                .body("result", equalTo("success"))
                .body("echo.test", equalTo(123));

        // Second request with same key and payload should return cached response
        given()
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/api/v1/test-idempotency")
                .then()
                .statusCode(200)
                .body("result", equalTo("success"))
                .body("echo.test", equalTo(123));

        assertEquals(1, IdempotentRequest.count());
    }

    @Test
    @TestSecurity(user = "idempuser", roles = { "user" })
    void testDifferentPayloadSameKey() {
        String key = UUID.randomUUID().toString();
        
        // First request
        given()
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body("{\"test\":1}")
                .when().post("/api/v1/test-idempotency")
                .then()
                .statusCode(200);

        // Second request with different body
        given()
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body("{\"test\":2}")
                .when().post("/api/v1/test-idempotency")
                .then()
                .statusCode(409); // Should return conflict because hash differs
                
        assertEquals(1, IdempotentRequest.count());
    }

    @Test
    @TestSecurity(user = "idempuser", roles = { "user" })
    void testConcurrentRequests() throws Exception {
        String key = UUID.randomUUID().toString();
        String payload = "{\"test\":\"concurrent\"}";

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Start first slow request
        Future<Integer> req1 = executor.submit(() -> given()
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/api/v1/test-idempotency/slow")
                .statusCode());

        // Wait for the lock to be created in the database
        await().atMost(5, TimeUnit.SECONDS).until(() -> QuarkusTransaction.requiringNew().call(() -> IdempotentRequest.count("idempotencyKey = ?1", key) > 0));

        // Start second request
        Future<Integer> req2 = executor.submit(() -> given()
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/api/v1/test-idempotency/slow")
                .statusCode());

        int status1 = req1.get(2, TimeUnit.SECONDS);
        int status2 = req2.get(2, TimeUnit.SECONDS);

        // One should succeed (200), the other should conflict (409) because the first one holds the lock
        assertTrue((status1 == 200 && status2 == 409) || (status1 == 409 && status2 == 200));

        executor.shutdown();
    }

    @Test
    @TestSecurity(user = "idempuser", roles = { "user" })
    void testErrorResponseNotCached() {
        String key = UUID.randomUUID().toString();
        String payload = "{\"test\":\"error\"}";

        // First request should fail with 500
        given()
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/api/v1/test-idempotency/error")
                .then()
                .statusCode(500);

        // The lock should have been removed since it failed
        assertEquals(0, IdempotentRequest.count());
        
        // A subsequent request with same key shouldn't hit cache or conflict,
        // it should execute again and hit the 500.
        given()
                .header("Idempotency-Key", key)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/api/v1/test-idempotency/error")
                .then()
                .statusCode(500);
    }
}
