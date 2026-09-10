package com.chandru.bankmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the Spring application context loads without errors.
 *
 * Test properties (src/test/resources/application.properties) provide:
 *  - jwk-set-uri so NimbusJwtDecoder is constructed without an OIDC
 *    discovery call (no live Keycloak needed)
 *  - H2 in-memory datasource so no PostgreSQL is needed
 */
@SpringBootTest
class BankManagementApplicationTests {

    @Test
    void contextLoads() {
        // passes if the application context starts without errors
    }
}
