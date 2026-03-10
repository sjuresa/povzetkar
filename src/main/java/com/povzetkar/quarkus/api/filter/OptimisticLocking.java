package com.povzetkar.quarkus.api.filter;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * JAX-RS Name Binding annotation to trigger the OptimisticLockingFilter.
 * Apply this to resource methods (PUT, PATCH, DELETE) that require
 * an If-Match header check for optimistic locking.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface OptimisticLocking {
}
