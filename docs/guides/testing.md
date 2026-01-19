# Testing Guide

This guide outlines the primary patterns and best practices for writing unit and integration tests in this project.

## 1. Core Philosophy

- **Pragmatism:** Use the simplest testing approach that provides confidence. Not everything needs a complex integration test.
- **Isolation:** Tests should be independent and runnable in any order. State created in one test should not affect another.
- **Clarity:** A test should clearly document the behavior it is verifying.

## 2. Test Types

### 2.1. Unit Tests

Unit tests verify a single class in isolation. They are fast and should be the most common type of test.

- **Framework:** `@QuarkusTest` can be used to test CDI beans, but it's not strictly necessary for simple classes.
- **Mocking:** Use `@InjectMock` to provide mock implementations of any collaborators.
- **Example (`ContentItemFetcherTest.java`):**
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
          when(strategyFactory.getStrategy(source.sourceTypeId)).thenReturn(mockStrategy);

          // When
          final LocalDate fixedDate = LocalDate.of(2023, 1, 1);
          contentItemFetcher.fetchItems(source, fixedDate, 10L, "stop-id");
          // Then
          verify(strategyFactory).getStrategy(source.sourceTypeId);
          verify(mockStrategy).fetch(source, fixedDate, 10L, "stop-id");
      }
  }
  ```

### 2.2. Integration Tests

Integration tests verify the interaction between multiple components, including the database and other infrastructure.

- **Annotation:** Use `@QuarkusTest` to run the test within a full Quarkus application context.
- **Database State:**
    - **`@TestTransaction`:** This is the **preferred** method. It automatically starts a transaction before each test and rolls it back afterward, ensuring a clean database state for every test without manual cleanup.
    - **Manual Cleanup:** If `@TestTransaction` is not used, you must manually clean up all database records in a `@BeforeEach` or `@AfterEach` method using Panache's `deleteAll()` methods. This is the current pattern in `OrchestrationIntegrationTest.java` but should be migrated to `@TestTransaction` when possible.
- **Mocking External Services:** Use `@InjectMock` to mock services that communicate with external systems (e.g., `YouTubeContentFetcherService`, `GenAiClient`). This keeps tests fast and reliable.
- **Do Not Mock Panache Entities:** Panache entities should be used directly to set up test data and assert against the database state. Mocking them (`PanacheMock`) is an antipattern for integration tests.

- **Example (Preferred `@TestTransaction` approach):**
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
          // No manual cleanup is needed.
          ContentSource testSource = new ContentSource();
          testSource.sourceIdentifier = "UC_test_channel";
          testSource.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
          testSource.name = "Test Channel";
          testSource.persist();

          when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), any(LocalDate.class), anyLong(), anyString()))
              .thenReturn(List.of(/* mock YouTubeVideo objects that will be processed */));
          // When
          orchestrationManager.runOrchestrationManually(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 1), true, true, true).await().indefinitely();
          // Then
          assertEquals(1, ContentItem.count());
          verify(youTubeContentFetcherService).fetchYouTubeVideos(any(ContentSource.class), any(LocalDate.class), anyLong(), anyString());
      }
  }
  ```

## 3. Testing Reactive Code

When testing methods that return a `Uni` or `Multi`, you must block execution to await the result.

- **`uni.await().indefinitely()`:** Use this to wait for a `Uni` to complete and get its result.
- **`multi.collect().asList().await().indefinitely()`:** Use this to collect all items from a `Multi` into a list.

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

To prevent classpath conflicts (e.g., `NoSuchMethodError`) and ensure compatibility during framework upgrades, **do not** specify explicit versions for test libraries in `pom.xml`.

- **Quarkus BOM:** Rely on the `quarkus-bom` to manage versions for:
    - `junit-jupiter`
    - `mockito-core` & `mockito-junit-jupiter`
    - `rest-assured`
- **Action:** When adding a new test dependency, check if it is managed by the BOM first. If it is, omit the `<version>` tag.
