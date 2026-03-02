# Mastering Testing in Povzetkar

This document outlines the standard practices and patterns used for testing in the Povzetkar project.

## Test Data Generation: The Builder Pattern

To streamline the generation of valid test entities and avoid verbosity in test setup, we rely on the **Test Data Builder Pattern**.

### Why use Builders?
In version v0.10.0, as core models (`User`, `Topic`, `ContentSource`) gained mandatory constraints, directly instantiating and configuring these entities in every test led to excessive boilerplate and brittle tests. Modifying an entity's required fields structurally broke dozens of unrelated tests.

### How to use Builders
We have centralized test entity construction in `src/test/java/com/povzetkar/quarkus/builder`. 

When setting up test data, **always** use builders instead of the `new` keyword:

```java
// DO THIS:
User user = UserBuilder.aUser().build();

// DO NOT DO THIS:
User user = new User();
user.username = "test";
user.email = "test@example.com";
user.name = "Test Name";
```

The builders are configured to provide valid, randomized defaults (like unique usernames/emails) to prevent constraint violations. 

To override specific properties, chain methods:
```java
User adminUser = UserBuilder.aUser().withUsername("admin").build();

Topic topic = TopicBuilder.aTopic()
    .withName("Custom Topic")
    .withUser(adminUser)
    .disabled()
    .build();
```

### Available Builders
- `UserBuilder`
- `TopicBuilder`
- `ContentSourceBuilder`

Adding new entities? Please implement a matching builder in the `builder` package containing sane default values.
