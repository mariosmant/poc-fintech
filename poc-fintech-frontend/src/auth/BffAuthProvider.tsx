import { useEffect, useRef, useState, type ReactNode } from 'react';
import { fetchBffUser, bffLogout, bootstrapCsrf, type BffUser } from '../api/bffClient';
import { AuthContext, useAuth, type AuthContextType } from './authContext';

// Re-export under the legacy name so any existing
// `import { useBffAuth } from '../../auth/BffAuthProvider'` keeps working.
export { useAuth as useBffAuth };

/**
 *
 * <p>Drop-in replacement for {@link AuthProvider} activated when
 * <code>VITE_AUTH_MODE === 'bff'</code>. Differences vs. the keycloak-js
 * variant:</p>
 * <ul>
 *   <li>No token in JS memory — the backend holds the access/refresh token
 *       server-side, the browser only ever sees the <code>__Host-SESSION</code>
 *       HttpOnly cookie.</li>
 *   <li>Identity is bootstrapped with <code>GET /bff/user</code> (same-origin).</li>
 *   <li>On 401 the SPA performs a <b>top-level navigation</b> to
 *       <code>/oauth2/authorization/keycloak</code> — not a fetch — because browsers
 *       cannot follow 302-to-IdP from fetch (opaque redirect).</li>
 *   <li>Logout calls <code>POST /bff/logout</code> which triggers Spring's
 *       OidcClientInitiatedLogoutSuccessHandler → Keycloak end_session_endpoint.</li>
 * </ul>
 *
 * @see BFF + __Host-cookie hardening
 */


/** Top-level navigation to the Spring Security OAuth2 client authorization endpoint. */
function triggerBffLogin(): void {
  // Preserve the page the user wanted so the backend can bounce back here post-login.
  // Spring stores the SavedRequest server-side; we only need to start the flow.
  window.location.assign('/oauth2/authorization/keycloak');
}

export function BffAuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<BffUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  // Single-flight guard — React 19 StrictMode double-invokes effects in dev.
  const bootstrapped = useRef(false);

  useEffect(() => {
    if (bootstrapped.current) return;
    bootstrapped.current = true;

    // Bootstrap the CSRF cookie first so the SPA can make mutating calls.
    bootstrapCsrf().catch(() => {
      /* non-fatal: will retry on first mutating request */
    });

    fetchBffUser()
      .then((u) => {
        if (u.authenticated) {
          setUser(u);
        } else {
          // No session — kick off the code flow via top-level nav.
          triggerBffLogin();
        }
      })
      .catch((err) => {
        // Network or server error — surface loading=false so an error boundary
        // or retry button can render.
        console.error('BFF user bootstrap failed', err);
      })
      .finally(() => setIsLoading(false));
  }, []);

  const contextValue: AuthContextType = {
    isAuthenticated: !!user,
    isLoading,
    userId: user?.subject ?? null,
    username: user?.username ?? null,
    token: null,
    roles: user?.roles ?? [],
    isAdmin: user?.admin ?? false,
    logout: () => {
      // Best-effort server logout, then navigate home. Spring's success handler
      // will have already redirected through Keycloak's end_session_endpoint
      // on the backend side before we arrive here.
      void bffLogout().finally(() => window.location.assign('/'));
    },
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4" />
          <p className="text-gray-500 text-sm">Authenticating…</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-gray-900 mb-4">💰 POC Fintech</h1>
          <p className="text-gray-500 mb-6">Redirecting to sign-in…</p>
          <button
            onClick={triggerBffLogin}
            className="px-6 py-3 bg-primary-600 text-white rounded-lg font-medium hover:bg-primary-700"
          >
            Sign In
          </button>
        </div>
      </div>
    );
  }

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
}

