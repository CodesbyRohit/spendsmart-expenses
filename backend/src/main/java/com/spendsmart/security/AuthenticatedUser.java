package com.spendsmart.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * The principal stored in the HTTP session after sign-in. Deliberately holds no password hash, so nothing secret is
 * kept in session memory or would be exposed if the principal were ever serialised.
 */
public record AuthenticatedUser(UUID id, String email, int credentialVersion) implements Principal, Serializable {

    @Override
    public String getName() {
        return id.toString();
    }
}
