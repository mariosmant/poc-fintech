import keycloak from './keycloak';

/**
 * Single-flight guard around `keycloak.login()`.
 *
 * Under background polling (React Query) and React StrictMode double-mount
 * it is easy for multiple code paths (token refresh failure, 401 response,
 * refresh interval) to invoke `keycloak.login()` concurrently. Each call
 * performs `window.location.assign(...)` with a newly minted OAuth `state`
 * parameter, producing visible infinite redirects with stacking `?state=…`
 * query params. This helper ensures exactly one login redirect is issued
 * per page lifetime.
 */
let loginInFlight = false;

export function redirectToLogin(): void {
  if (loginInFlight) return;
  loginInFlight = true;
  try {
    keycloak.login();
  } catch (err) {
    // If the redirect fails to initiate, allow a future retry.
    loginInFlight = false;
    throw err;
  }
}

/** Test-only helper. */
export function __resetLoginGuard(): void {
  loginInFlight = false;
}

