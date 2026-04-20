import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import keycloak from './keycloak';

interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  userId: string | null;
  username: string | null;
  token: string | null;
  roles: string[];
  logout: () => void;
}

const AuthContext = createContext<AuthContextType>({
  isAuthenticated: false,
  isLoading: true,
  userId: null,
  username: null,
  token: null,
  roles: [],
  logout: () => {},
});

/**
 * Authentication provider wrapping the app with Keycloak OIDC.
 * Initializes Keycloak with login-required — no anonymous access.
 * Auto-refreshes tokens before expiry.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    keycloak
      .init({
        onLoad: 'login-required',
        pkceMethod: 'S256',
        checkLoginIframe: false,
      })
      .then((authenticated) => {
        setIsAuthenticated(authenticated);
        setIsLoading(false);

        // Auto-refresh token before expiry
        if (authenticated) {
          setInterval(() => {
            keycloak.updateToken(30).catch(() => {
              console.warn('Token refresh failed, redirecting to login');
              keycloak.login();
            });
          }, 30_000);
        }
      })
      .catch((err) => {
        console.error('Keycloak init failed', err);
        setIsLoading(false);
      });
  }, []);

  const contextValue: AuthContextType = {
    isAuthenticated,
    isLoading,
    userId: keycloak.subject ?? null,
    username: keycloak.tokenParsed?.preferred_username ?? null,
    token: keycloak.token ?? null,
    roles: keycloak.tokenParsed?.realm_access?.roles ?? [],
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
            onClick={() => keycloak.login()}
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

