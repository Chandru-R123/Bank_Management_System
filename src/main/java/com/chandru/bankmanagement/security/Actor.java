package com.chandru.bankmanagement.security;

/**
 * The authenticated caller, resolved from the Keycloak JWT.
 *
 * @param sub       Keycloak user UUID ("sub" claim)
 * @param username  preferred_username — recorded on transactions for audit
 * @param admin     caller has the ADMIN realm role
 * @param staff     caller has the ADMIN or EMPLOYEE realm role
 */
public record Actor(String sub, String username, boolean admin, boolean staff) {

    /** Customers must own the account they operate on; staff need not. */
    public boolean requiresOwnership() {
        return !staff;
    }
}
