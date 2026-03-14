package com.povzetkar.quarkus.api.filter;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * JAX-RS Name Binding annotation to trigger the GlobalEnvelopeFilter.
 * Apply this to resource classes or methods that should have their successful
 * JSON responses wrapped in an ApiEnvelopeDTO.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Enveloped {
}
