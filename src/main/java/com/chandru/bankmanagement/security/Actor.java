package com.chandru.bankmanagement.security;

/**
 * The authenticated caller, resolved from the Keycloak JWT.
 *
 * @param sub       Keycloak user UUID ("sub" claim)
 * @param username  preferred_username — recorded on transactions for audit
 * @param admin     ADMIN realm role
 * @param staff     ADMIN, EMPLOYEE, MAKER or CHECKER — may VIEW every customer/account
 * @param maker     ADMIN or MAKER — may MOVE money on any account
 * @param checker   ADMIN or CHECKER — may perform verification actions
 * @param tpp       TPP realm role — a third-party provider application
 */
public record Actor(String sub, String username, boolean admin, boolean staff,
                    boolean maker, boolean checker, boolean tpp) {

    /** Money movement: only makers may act on accounts they do not own. */
    public boolean requiresOwnership() {
        return !maker;
    }
}
