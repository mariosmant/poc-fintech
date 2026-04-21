import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import keycloak from './keycloak';
import { redirectToLogin } from './loginGuard';

interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  userId: string | null;
  username: string | null;
  token: string | null;
  roles: string[];
  isAdmin: boolean;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType>({
  isAuthenticated: false,
  isLoading: true,
  userId: null,
  username: null,
  token: null,
  roles: [],
  isAdmin: false,
  logout: () => {},
});

/**
 * Module-level singleton promise guarding `keycloak.init()`.
 *
 * React 18/19 StrictMode intentionally double-invokes effects in development.
 * Calling `keycloak.init()` twice on the same instance throws and — because
 * the first invocation may still be consuming `?state=&session_state=&code=`
 * query params from the OIDC redirect — leaves the app stuck in a redirect
 * loop where every mount issues a fresh `login()` with a new `state` value.
 */
let initPromise: Promise<boolean> | null = null;

function initKeycloakOnce(): Promise<boolean> {
  if (!initPromise) {
    initPromise = keycloak
      .init({
        onLoad: 'login-required',
        pkceMethod: 'S256',
        checkLoginIframe: false,
      })
      .catch((err) => {
        initPromise = null; // allow a manual reload to retry
        throw err;
      });
  }
  return initPromise;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    let refreshTimer: ReturnType<typeof setInterval> | null = null;

    initKeycloakOnce()
      .then((authenticated) => {
        if (cancelled) return;
        setIsAuthenticated(authenticated);
        setIsLoading(false);

        if (authenticated) {
          refreshTimer = setInterval(() => {
            keycloak.updateToken(30).catch(() => {
              console.warn('Token refresh failed, redirecting to login');
              redirectToLogin();
            });
          }, 30_000);
        }
      })
      .catch((err) => {
        if (cancelled) return;
        console.error('Keycloak init failed', err);
        setIsLoading(false);
      });

    return () => {
      cancelled = true;
      if (refreshTimer) clearInterval(refreshTimer);
    };
  }, []);

  const roles: string[] = keycloak.tokenParsed?.realm_access?.roles ?? [];
  const contextValue: AuthContextType = {
    isAuthenticated,
    isLoading,
    userId: keycloak.subject ?? null,
    username: keycloak.tokenParsed?.preferred_username ?? null,
    token: keycloak.token ?? null,
    roles,
    isAdmin: roles.includes('admin'),
    logout: () => keycloak.logout({ redirectUri: window.location.origin }),
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

  if (!isAuthenticated) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-gray-900 mb-4">💰 POC Fintech</h1>
          <p className="text-gray-500 mb-6">Please log in to continue</p>
          <button
            onClick={() => redirectToLogin()}
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

/** Hook to access authentication state. */
export function useAuth() {
  return useContext(AuthContext);
}

