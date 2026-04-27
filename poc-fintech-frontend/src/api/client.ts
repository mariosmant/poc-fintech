/**
 * Centralized API client for the POC Fintech backend.
 *
 * Supports both auth modes:
 * - Resource-Server (default): Bearer JWT from Keycloak-JS in Authorization header.
 * - BFF (when SPRING_PROFILES_ACTIVE=bff): same-origin session cookie + double-submit
 *   CSRF token (echoes the __Host-XSRF-TOKEN cookie into the X-XSRF-TOKEN header on
 *   state-changing methods). Safe methods are exempt.
 */
import type {
  Account,
  CreateAccountRequest,
  InitiateTransferRequest,
  LedgerEntry,
  ProblemDetail,
  Transfer,
} from '../types/api';
import keycloak from '../auth/keycloak';
import { redirectToLogin } from '../auth/loginGuard';

const BASE = '/api/v1';

/** Custom error class carrying the RFC 7807 ProblemDetail body. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: ProblemDetail,
  ) {
    super(problem.detail ?? problem.title);
    this.name = 'ApiError';
  }
}

/** Read a cookie value by name. Returns empty string if missing. */
function readCookie(name: string): string {
  if (typeof document === 'undefined') return '';
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
 * Returns authorization headers with the current Keycloak JWT (Resource-Server mode).
 * In BFF mode no Bearer token is held by the SPA — the session cookie carries auth
 * and the X-XSRF-TOKEN header carries CSRF, both attached in {@link request} below.
 */
async function getAuthHeaders(): Promise<Record<string, string>> {
  if (keycloak.authenticated) {
    // Refresh token if it expires within 30 seconds
    try {
      await keycloak.updateToken(30);
    } catch {
      redirectToLogin();
      throw new Error('Token refresh failed');
    }
    return {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${keycloak.token}`,
    };
  }
  return { 'Content-Type': 'application/json' };
}

/**
 * Generic fetch wrapper with error handling, JWT/Bearer auth (Resource-Server)
 * or session-cookie + CSRF auth (BFF). Throws {@link ApiError} on non-2xx responses.
 */
async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const authHeaders = await getAuthHeaders();
  const method = (init?.method ?? 'GET').toUpperCase();
  const headers = new Headers({ ...authHeaders, ...(init?.headers as Record<string, string> | undefined) });

  // BFF double-submit CSRF: when the __Host-XSRF-TOKEN cookie is present
  // (server-issued by CookieCsrfTokenRepository in BFF mode), echo it as the
  // X-XSRF-TOKEN header on mutating requests. No-op in Resource-Server mode.
  if (needsCsrf(method)) {
    const csrf = readCookie('__Host-XSRF-TOKEN');
    if (csrf && !headers.has('X-XSRF-TOKEN')) {
      headers.set('X-XSRF-TOKEN', csrf);
    }
  }

  const res = await fetch(`${BASE}${url}`, {
    ...init,
    method,
    headers,
    // Same-origin only — Vite dev proxy + reverse proxy make /api same-origin
    // with the SPA, so the __Host-SESSION cookie is auto-attached. Never 'include'.
    credentials: 'same-origin',
  });

  if (res.status === 401) {
    // Token expired or invalid — redirect to login (single-flight)
    redirectToLogin();
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
    throw new ApiError(res.status, problem);
  }

  // 204 No Content
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

// ── Accounts ──────────────────────────────────────────────────────────

export const accountsApi = {
  /** Create a new account. */
  create: (data: CreateAccountRequest) =>
    request<Account>('/accounts', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  /** Get account by ID. */
  getById: (id: string) => request<Account>(`/accounts/${id}`),

  /** List all accounts ordered by most recent first. */
  list: () => request<Account[]>('/accounts'),
};

// ── Transfers ─────────────────────────────────────────────────────────

export const transfersApi = {
  /** Initiate a money transfer. */
  initiate: (data: InitiateTransferRequest) =>
    request<Transfer>('/transfers', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  /** Get transfer by ID. */
  getById: (id: string) => request<Transfer>(`/transfers/${id}`),

  /** List latest transfers for monitoring views. */
  list: (limit = 50) => request<Transfer[]>(`/transfers?limit=${limit}`),
};

// ── Ledger ────────────────────────────────────────────────────────────

export const ledgerApi = {
  /** Get ledger entries by account ID. */
  getByAccount: (accountId: string) =>
    request<LedgerEntry[]>(`/ledger/account/${accountId}`),

  /** Get ledger entries by transfer ID. */
  getByTransfer: (transferId: string) =>
    request<LedgerEntry[]>(`/ledger/transfer/${transferId}`),

  /** Get recent ledger entries for monitoring dashboards. */
  recent: (limit = 100) => request<LedgerEntry[]>(`/ledger/recent?limit=${limit}`),
};
