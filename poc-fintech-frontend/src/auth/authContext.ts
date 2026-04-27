import { createContext, useContext } from 'react';

/**
 * Shared auth-context shape consumed by every page (`useAuth()`).
 *
 * Both {@link AuthProvider} (keycloak-js Bearer flow) and
 * {@link BffAuthProvider} (server-held tokens behind
 * `__Host-SESSION`) populate THIS context, so feature pages don't need to
 * know which auth mode is active.
 *
 * Historical bug: each provider used to declare its own private
 * `createContext(...)` instance, so `useAuth()` (re-exported from
 * `AuthProvider.tsx`) read the JWT-provider's empty defaults under
 * `BffAuthProvider`, producing the "User" placeholder + no-op logout
 * symptoms.  Centralising the context here permanently prevents that.
 */
export interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  userId: string | null;
  username: string | null;
  /** Bearer token in JWT mode; always `null` in BFF mode. */
  token: string | null;
  roles: string[];
  isAdmin: boolean;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextType>({
  isAuthenticated: false,
  isLoading: true,
  userId: null,
  username: null,
  token: null,
  roles: [],
  isAdmin: false,
  logout: () => {},
});

/** Hook to access authentication state — provider-agnostic. */
export function useAuth(): AuthContextType {
  return useContext(AuthContext);
}
