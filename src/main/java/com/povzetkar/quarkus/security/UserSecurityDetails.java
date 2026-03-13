package com.povzetkar.quarkus.security;

import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * A simple record to capture security principal information from the main thread
 * so it can be used on worker threads where the original request context may be lost.
 */
public record UserSecurityDetails(String principalName, String jwtSub) {
    
    /**
     * Captures details from the provided {@link SecurityIdentity}.
     * 
     * @param identity the security identity to capture from
     * @return current user details, or an empty details record if anonymous
     */
    public static UserSecurityDetails capture(SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous()) {
            return new UserSecurityDetails(null, null);
        }
        
        String name = identity.getPrincipal().getName();
        String sub = (identity.getPrincipal() instanceof JsonWebToken jwt) 
                ? jwt.getSubject() : null;
        
        return new UserSecurityDetails(name, sub);
    }
}
