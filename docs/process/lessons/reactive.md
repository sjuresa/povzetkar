# Reactive Programming (Mutiny)

## Separation of I/O and Transformation
When building reactive pipelines with Mutiny (e.g., `Uni` or `Multi`), strictly separate blocking operations from data transformations.
- **The Trap:** Mixing database queries and DTO mapping within the same worker-thread block.
- **The Standard:** Use worker threads purely for blocking I/O and switch back to the event loop using `.emitOn(Infrastructure.getDefaultExecutor())` before performing CPU-bound transformations via `.map()`.

## Blocking Initial Logic in Reactive Endpoints
- **The Trap:** Assuming a method returning `Uni` is automatically "safe" from blocking the event loop. The method body itself starts execution on the event loop.
- **The Standard:** If any logic *before* the first asynchronous operation is blocking, use `@Blocking` or offload it explicitly: `Uni.createFrom().item(this::getBlockingData).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())`.

## Parallelizing Independent Queries (BFF Aggregation)
When building "Backend-for-Frontend" (BFF) aggregation endpoints:
- **The Trap:** Executing database queries sequentially leads to additive latency.
- **The Standard:** Use `Uni.combine().all().unis(u1, u2, u3).with(this::assemble)` for parallel execution.

## Transaction Type Safety in Pipelines
- **The Trap:** Assuming `QuarkusTransaction.requiringNew().call(Supplier<T>)` returns a reactive `Uni<T>`. In Hibernate ORM, it returns the raw type `T`.
- **The Standard:** Wrap the call manually: `Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(supplier::get)).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())`.

## Collection Type Safety in Mutiny
- **The Trap:** Using `List.of()` as a default value in a reactive pipeline. Without explicit type arguments, it creates a `List<Object>`, which can cause `ClassCastException` at runtime due to type erasure.
- **The Standard:** Always prefer **`Collections.emptyList()`** for empty collection defaults in reactive chains.

# Server-Sent Events (SSE) & Real-Time Updates

## Resource Management (@PreDestroy)
- **The Trap:** Opening a `BroadcastProcessor` in a `@Singleton` bean without a cleanup hook causes resource leaks during hot reloads or shutdown.
- **The Standard:** Always implement a `@PreDestroy` method to explicitly close or complete the processor.

## SSE Event Order in Integration Tests
- **The Trap:** Firing business events immediately after starting an `SseEventSource` in a test results in race conditions.
- **The Standard:** Push an initial `CONNECTED` event from the `GET` stream endpoint and use `await().until(...)` in the test to verify receipt of this event before triggering business logic. This guarantees the subscriber is active.
