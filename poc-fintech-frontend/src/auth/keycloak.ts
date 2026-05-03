import Keycloak from 'keycloak-js';

/**
 * Keycloak instance configuration for POC Fintech.
 * Uses PKCE (S256) for security — public client, no client secret.
 */
const keycloak = new Keycloak({
  url: 'http://localhost:8180',
  realm: 'fintech',
  clientId: 'poc-fintech-spa',
});

export default keycloak;

