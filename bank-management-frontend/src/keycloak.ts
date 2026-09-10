import Keycloak from 'keycloak-js';

/**
 * Single Keycloak instance shared across the whole app.
 *
 * Configuration must match:
 *   - Keycloak realm  : bank-management
 *   - Keycloak client : bank-management-backend
 *   - Keycloak port   : 8180  (docker-compose.yml)
 */
const keycloak = new Keycloak({
  url:      'http://localhost:8180',
  realm:    'bank-management',
  clientId: 'bank-management-backend',
});

export default keycloak;
