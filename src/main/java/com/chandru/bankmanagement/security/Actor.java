package com.chandru.bankmanagement.security;

/**
 * The authenticated caller, resolved from the Keycloak JWT.
 *
 * @param sub         Keycloak user UUID ("sub" claim)
 * @param username    preferred_username — recorded on transactions and audit log
 * @param admin       ADMIN realm role
 * @param staff       ADMIN, EMPLOYEE, MAKER or CHECKER — may VIEW every customer/account
 * @param maker       has MAKER role (but NOT necessarily CHECKER)
 * @param checker     has CHECKER role (but NOT necessarily MAKER)
 * @param employee    has EMPLOYEE role
 * @param tpp         TPP realm role — a third-party provider application
 *
 * Important distinctions:
 *   - A user with BOTH MAKER and CHECKER roles cannot self-approve (enforced by sub comparison).
 *   - ADMIN can do everything but Maker–Checker self-approval is still blocked.
 *   - EMPLOYEE cannot approve Maker requests (checker() returns false for pure EMPLOYEE).
 */
public record Actor(String sub, String username, boolean admin, boolean staff,
                    boolean maker, boolean checker, boolean employee, boolean tpp) {

    /**
     * Money movement: Customers must own the account; staff makers can operate on any account.
     * Pure CHECKER, EMPLOYEE, and TPP require ownership (they cannot move money on arbitrary accounts).
     */
    public boolean requiresOwnership() {
        return !maker && !admin;
    }

    /** True if this actor has the MAKER role (ADMIN also returns true for backward compat). */
    public boolean isMaker() {
        return maker || admin;
    }

    /** True if this actor has the CHECKER role (ADMIN also returns true). */
    public boolean isChecker() {
        return checker || admin;
    }

    /** True if this actor is a pure MAKER without CHECKER authority. */
    public boolean isPureMaker() {
        return maker && !checker && !admin;
    }
}
