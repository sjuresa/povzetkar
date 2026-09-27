# Testing Guide

This guide outlines the primary patterns and best practices for writing unit and integration tests in this project. It clarifies the distinction between `@QuarkusTest` and `@QuarkusIntegrationTest` to ensure tests are written at the correct level of abstraction.

## 1. Core Philosophy

- **Pragmatism:** Use the simplest testing approach that provides confidence. Not everything needs a complex integration test.
- **Isolation:** Tests should be independent and runnable in any order. State created in one test should not affect another.
- **Clarity:** A test should clearly document the behavior it is verifying.

## 2. Test Types & Annotations

### 2.1. Component/Unit Tests (`@QuarkusTest`)

Use `@QuarkusTest` for testing individual components (services, resources) where you need dependency injection but want to mock external collaborators.

-   **Execution:** Runs in the same JVM as the test runner. Uses a separate classloader to isolate the application code from the test code.
-   **Mocking:** Use `@InjectMock` to provide mock implementations of collaborators.
-   **Use Case:** Testing business logic, service orchestration, and internal API behavior.

**Example (`ContentItemFetcherTest.java`):**
```java
@QuarkusTest
class ContentItemFetcherTest {
    @Inject
    ContentItemFetcher contentItemFetcher;

    @InjectMock
    ContentFetchingStrategyFactory strategyFactory;

    @Test
    void testFetchItems() {
        // Given
        ContentSource source = new ContentSource();
        source.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        ContentFetchingStrategy mockStrategy = Mockito.mock(ContentFetchingStrategy.class);
        List<ContentItem> expectedItems = List.of(new ContentItem()); // Dummy list for assertion
        when(strategyFactory.getStrategy(source.sourceTypeId)).thenReturn(mockStrategy);
        final LocalDate fixedDate = LocalDate.of(2023, 1, 1);
        when(mockStrategy.fetch(source, fixedDate, 10L, "stop-id")).thenReturn(expectedItems);
        
        // When
        List<ContentItem> actualItems = contentItemFetcher.fetchItems(source, fixedDate, 10L, "stop-id");
        
        // Then
        assertEquals(expectedItems, actualItems);
        verify(strategyFactory).getStrategy(source.sourceTypeId);
        verify(mockStrategy).fetch(source, fixedDate, 10L, "stop-id");
    }
}
```

### 2.2. Integration/E2E Tests (`@QuarkusIntegrationTest`)

Use `@QuarkusIntegrationTest` for black-box testing of the packaged application.

-   **Execution:** Runs the application in a separate process (or container). The test runs in a different JVM and communicates with the app via HTTP (RestAssured).
-   **Isolation:** Full process isolation. No bean injection (`@Inject`) is possible in the test class itself.
-   **Use Case:** Verifying the final build artifact (JAR, Native Image, Container), testing HTTP endpoints end-to-end, and validating configuration.
-   **Prerequisites:** Ensure Docker is running if your application relies on Quarkus DevServices (e.g., for PostgreSQL), as the isolated process will attempt to start necessary containers.

**Example (`ItemResourceIT.java`):**
```java
@QuarkusIntegrationTest
public class ItemResourceIT {
    @Test
    void testItemsEndpointFlow() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"My Test Item\"}")
            .post("/items")
            .then()
            .statusCode(201);

        RestAssured.get("/items")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(1))
            .body("[0].name", is("My Test Item"));
    }
}
```

### 2.3. Database Integration Tests (`@QuarkusTest` with `@TestTransaction`)

For tests that need to interact with the database but don't require full process isolation, use `@QuarkusTest` combined with `@TestTransaction`.

-   **`@TestTransaction`:** Automatically starts a transaction before each test and rolls it back afterward. This is the **preferred** method for database tests as it ensures a clean state without manual cleanup. Exception: a test that commits on purpose — e.g. interleaving transactions with `QuarkusTransaction.requiringNew()` — escapes the rollback and must clean up itself (`@AfterEach`); see §2.5.
-   **Scope:** which tests run against the database is defined by QA rule 3 in `docs/protocols/quality-mandates.md`; see §2.5 for the pattern.

> **Note:** `@TestTransaction` works best with blocking code. If testing complex reactive pipelines where context propagation might be lost, ensure you understand how the transaction context is handled.

**Example (`OrchestrationIntegrationTest.java`):**
```java
@QuarkusTest
class OrchestrationIntegrationTest {
    @Inject
    OrchestrationManager orchestrationManager;

    @InjectMock
    YouTubeContentFetcherService youTubeContentFetcherService;

    @Test
    @TestTransaction // Ensures transaction is rolled back after the test
    void testOrchestrationFlow_success() {
        // Given: Set up test data within the transactional test method.
        ContentSource testSource = new ContentSource();
        testSource.sourceIdentifier = "UC_test_channel";
        testSource.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        testSource.name = "Test Channel";
        testSource.persist();

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), any(LocalDate.class), anyLong(), anyString()))
            .thenReturn(List.of(new YouTubeVideo("vid1", "Test Video", "http://...", "Test Author", "UC_test_channel", Instant.parse("2023-01-01T10:00:00Z"), Duration.ofMinutes(10))));
        
        // When
        OrchestrationRequest request = new OrchestrationRequest(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 1), true, true, false);
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();
        
        // Then
        assertEquals(1, ContentItem.count());
        verify(youTubeContentFetcherService).fetchYouTubeVideos(any(ContentSource.class), any(LocalDate.class), anyLong(), anyString());
    }
}
```

### 2.4. Configuration Key Guard (`QuarkusConfigKeysTest`)

A plain JUnit test (no Quarkus boot) that fails when `application.properties` (main or test, any profile) contains a `quarkus.*` key no extension declares, or one only a passthrough map (e.g. `quarkus.micrometer.export.prometheus.*`) would swallow. Quarkus itself ignores such keys silently — see `docs/process/lessons/infrastructure-ops.md`, "Quarkus Configuration Keys".

-   **How it knows the valid keys:** the Maven profile `config-guard` (active unless `skipTests` is true) runs `quarkus:dependency-tree` once per launch mode into `target/quarkus-dependency-tree-{prod,dev,test}.txt`; the test reads `META-INF/quarkus-config-doc/quarkus-config-model.json` from every listed jar in the local Maven repository. `%prod`/`%dev`/`%test` keys are checked against their own mode, unprofiled keys against the mode their file takes effect in (`src/main`: prod, `src/test`: test). Surefire passes the paths as system properties from the same profile, so run it through Maven (`./mvnw test -pl backend -Dtest=QuarkusConfigKeysTest`); an IDE run or Quarkus continuous testing (no such properties) skips the test instead of failing. Multi-profile keys (`%dev,test.`) must be declared in each listed mode.
-   **What it rejects:** unknown keys; keys only a passthrough map would swallow; named-map keys that are really a fixed segment in the wrong place (`quarkus.datasource.jdbc.db-kind` read as datasource "jdbc"); unprofiled keys not declared for the mode their file takes effect in. It cannot catch a typo in a genuine instance name (e.g. a rest-client config key).
-   **When it fails:** fix the key against the extension's config reference, or move a custom setting to `povzetkar.*`. Only if the key is genuinely valid (e.g. a passthrough entry you verified the library reads) add it to the test's `ALLOWLIST` with a reason.

### 2.5. Entity Once Against the DB, Logic With Panache Mocked

How to apply QA rule 3 (`docs/protocols/quality-mandates.md`) to an entity and the service that uses it. Reference implementation: POV-216 (`UserOnboarding`, `OnboardingService`).

-   **Persistence test (real DB, entity only):** persist every column, `flush()` + `clear()`, reload and compare; cover constraints (FK cascade), custom queries (`findOrCreate` with `INSERT … ON CONFLICT DO NOTHING`) and concurrency — two transactions interleaved deterministically with nested `QuarkusTransaction.requiringNew()` (the outer loads a snapshot, the inner commits a change, the outer then writes). No service, no endpoint (`UserOnboardingPersistenceTest`).
-   **Domain logic:** rules on the entity (e.g. `apply(patch)`) get a plain JUnit test without Quarkus (`UserOnboardingTest`).
-   **Service test:** `PanacheMock.mock(Entity.class)` stubs the entity's static methods — Panache's finders and your own (`findOrCreate`); `@InjectMock Session` when the service calls instance methods such as `persist()`; `@InjectMock` for other collaborators (`OnboardingServiceTest`):

```java
@QuarkusTest
class OnboardingServiceTest {
    private static final long USER_ID = 42L;

    @Inject OnboardingService service;
    @InjectMock UserContextService userContextService;

    @BeforeEach
    void setUp() {
        PanacheMock.mock(UserOnboarding.class);       // every static method of the entity
    }

    @Test
    void getReturnsTheUsersState() {
        givenCurrentUser(AccountStatus.ENABLED);
        UserOnboarding state = new UserOnboarding(USER_ID);
        state.stepTopic = true;
        when(UserOnboarding.findOrCreate(USER_ID)).thenReturn(state);

        OnboardingDTO result = service.getForCurrentUser();

        assertEquals(OnboardingDTO.from(state), result);
        PanacheMock.verify(UserOnboarding.class).findOrCreate(USER_ID);
    }

    private void givenCurrentUser(AccountStatus status) {
        User user = UserBuilder.aUser().withId(USER_ID).withStatus(status).build();
        when(userContextService.getCurrentUser()).thenReturn(Optional.of(user));
    }
}
```

-   **Verify statics through `PanacheMock`:** `PanacheMock.verify(UserOnboarding.class, never()).findOrCreate(anyLong())` — `Mockito.verify` does not know the static mock.
-   **Trap — `PanacheMock` mocks every static method of the entity, custom ones included.** Useful for a custom query like `findOrCreate`, but a static factory the service expects to run for real (e.g. an `emptyFor(id)`) silently returns `null`. Create new instances with a constructor (or stub the factory with `thenCallRealMethod()`).
-   **Resource test:** `@InjectMock` the service and assert status codes, the JSON envelope and security with `@TestSecurity` (`OnboardingApiResourceTest`). An anonymous request to an OIDC `web-app` endpoint gets a `302` login redirect, not `401` — use `redirects().follow(false)`.

## 3. Testing Reactive Code

When testing methods that return a `Uni` or `Multi`, you must block execution to await the result.

-   **`uni.await().indefinitely()`:** Wait for a `Uni` to complete.
-   **`multi.collect().asList().await().indefinitely()`:** Collect all items from a `Multi`.

```java
@Test
void testReactiveMethod() {
    // When
    Uni<String> resultUni = service.getReactiveValue();
    String result = resultUni.await().indefinitely();

    // Then
    assertEquals("expected-value", result);
}
```

## 4. Dependency Management

To prevent classpath conflicts and ensure compatibility:

-   **Quarkus BOM:** Rely on the `quarkus-bom` to manage versions for `junit-jupiter`, `mockito`, and `rest-assured`.
-   **No Explicit Versions:** Do not specify `<version>` tags for these libraries in `pom.xml`.
