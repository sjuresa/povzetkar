package com.povzetkar.quarkus.api.filter;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark JAX-RS resource methods or classes as requiring idempotency.
 * The {@link IdempotencyFilter} will intercept requests to these endpoints, checking
 * the {@code Idempotency-Key} header to potentially return a cached response.
 */
@NameBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
