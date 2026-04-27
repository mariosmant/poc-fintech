/**
 * BFF (Backend-for-Frontend) HTTP client.
 *
 * <p>Implements the modern "BFF with HttpOnly session cookie" pattern recommended
 * by the IETF OAuth 2.0 for Browser-Based Apps draft (draft-ietf-oauth-browser-based-apps)
 * and NIST SP 800-63B AAL2 guidance:</p>
 *
 * <ul>
 *   <li><b>No tokens in JS memory or storage.</b> The SPA never touches the access or
 *       refresh token. All credentials live in the server-side session, exposed to
 *       the browser only as a <code>__Host-SESSION</code> HttpOnly cookie.</li>
 *   <li><b>Same-origin only.</b> Uses <code>credentials: 'same-origin'</code> — the browser
 *       auto-attaches the session cookie when the SPA and BFF share an origin (in dev via
 *       Vite proxy, in prod via reverse proxy). <b>Never</b> <code>'include'</code>, which
 *       would require cross-origin CORS + <code>Access-Control-Allow-Credentials: true</code>,
 *       enlarge the attack surface, and is disallowed by SameSite=Strict anyway.</li>
 *   <li><b>Double-submit CSRF.</b> If a <code>__Host-XSRF-TOKEN</code> cookie is present,
 *       the client echoes its value in the <code>X-XSRF-TOKEN</code> request header on
 *       state-changing methods. Safe methods (GET/HEAD/OPTIONS) are exempt.</li>
 *   <li><b>401 handling.</b> On 401 the SPA navigates to <code>/bff/login</code> (handled
 *       by Spring Security's OAuth2 Client in BFF mode). Single-flight guarded upstream
 *       by <code>loginGuard.ts</code> to prevent redirect storms.</li>
 * </ul>
 *
 * <p>This module is tree-shakeable and has no dependency on <code>keycloak-js</code> —
 * it is the drop-in replacement for {@code api/client.ts} once the backend is running
 * under the <code>bff</code> profile.</p>
 *
 * @see ../api/client.ts — legacy JWT-Bearer client
 */

import type { ProblemDetail } from '../types/api';

const API_BASE = '/api/v1';
const BFF_BASE = '/bff';

/** RFC 7807 error thrown by all bffRequest calls on non-2xx responses. */
export class BffApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: ProblemDetail,
  ) {
    super(problem.detail ?? problem.title);
    this.name = 'BffApiError';
  }
}

/** Read a cookie value by name. Returns empty string if missing. */
function readCookie(name: string): string {
  const target = `${name}=`;
  for (const raw of document.cookie.split(';')) {
    const c = raw.trim();
    if (c.startsWith(target)) return decodeURIComponent(c.substring(target.length));
  }
  return '';
}

/** Returns true for HTTP methods that need CSRF protection. */
function needsCsrf(method: string): boolean {
  const m = method.toUpperCase();
  return m !== 'GET' && m !== 'HEAD' && m !== 'OPTIONS' && m !== 'TRACE';
}

/**
 * Core same-origin fetch wrapper.
 *
 * <p>Always sends <code>credentials: 'same-origin'</code> so the __Host-SESSION cookie
 * is attached on same-origin requests and <b>never</b> leaks cross-origin. Automatically
 * injects the CSRF header on mutating methods when the __Host-XSRF-TOKEN cookie exists.</p>
 */
async function bffFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers ?? {});

  // JSON content-type default (only if body is present and header not set)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  // CSRF double-submit: echo the __Host-XSRF-TOKEN cookie on mutating requests.
  if (needsCsrf(method)) {
    const csrf = readCookie('__Host-XSRF-TOKEN');
    if (csrf) headers.set('X-XSRF-TOKEN', csrf);
  }

  // Defence-in-depth: opt the request out of Referer leakage.
  if (!headers.has('Referrer-Policy')) {
    headers.set('Referrer-Policy', 'same-origin');
  }

  return fetch(url, {
    ...init,
    method,
    headers,
    credentials: 'same-origin', // explicit; NEVER 'include'
    redirect: 'manual',         // surface auth redirects as opaqueredirect, not silent
    mode: 'same-origin',
  });
}

/**
 * Generic request helper with RFC 7807 error mapping and 401 redirect hook.
 *
 * @param onUnauthorized  callback invoked on 401 so the caller can decide how to
 *                        redirect (kept out of this module to avoid tight coupling
 *                        with a specific router / login-guard implementation).
 */
export async function bffRequest<T>(
  url: string,
  init: RequestInit = {},
  onUnauthorized: () => void = () => {},
): Promise<T> {
  const res = await bffFetch(url, init);

  if (res.type === 'opaqueredirect' || res.status === 401) {
    onUnauthorized();
    throw new Error('Unauthorized');
  }

  if (!res.ok) {
    const problem: ProblemDetail = await res.json().catch(() => ({
      type: 'urn:fintech:error:unknown',
      title: 'Request failed',
      status: res.status,
      detail: res.statusText,
      timestamp: new Date().toISOString(),
    }));
    throw new BffApiError(res.status, problem);
  }

  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

// ── BFF identity endpoints ────────────────────────────────────────────────

/** The projection of the authenticated user exposed by GET /bff/user. */
export interface BffUser {
  authenticated: true;
  subject: string;
  username: string;
  name?: string;
  email?: string;
  roles: string[];
  admin: boolean;
}

/** Anonymous projection returned with HTTP 401 when the session is missing/expired. */
export interface BffAnonymous {
  authenticated: false;
}

/** Fetches the current principal. Does NOT trigger a login redirect on 401. */
export async function fetchBffUser(): Promise<BffUser | BffAnonymous> {
  const res = await bffFetch(`${BFF_BASE}/user`);
  if (res.status === 401) return { authenticated: false };
  if (!res.ok) throw new Error(`/bff/user failed: HTTP ${res.status}`);
  return res.json();
}

/** Server-side logout: invalidates the session and clears __Host- cookies. */
export async function bffLogout(): Promise<void> {
  await bffFetch(`${BFF_BASE}/logout`, { method: 'POST' });
}

/** Bootstraps the CSRF token cookie (call once on app load in BFF mode). */
export async function bootstrapCsrf(): Promise<void> {
  await bffFetch(`${BFF_BASE}/public/csrf`);
}

// ── Typed API helpers (same contracts as legacy client) ───────────────────

export const bffApi = {
  /** Generic GET with full URL under /api/v1. */
  get: <T>(path: string, onUnauthorized?: () => void) =>
    bffRequest<T>(`${API_BASE}${path}`, { method: 'GET' }, onUnauthorized),

  /** Generic POST with JSON body. */
  post: <T>(path: string, body: unknown, onUnauthorized?: () => void) =>
    bffRequest<T>(
      `${API_BASE}${path}`,
      { method: 'POST', body: JSON.stringify(body) },
      onUnauthorized,
    ),

  /** Generic DELETE. */
  del: <T>(path: string, onUnauthorized?: () => void) =>
    bffRequest<T>(`${API_BASE}${path}`, { method: 'DELETE' }, onUnauthorized),
};

