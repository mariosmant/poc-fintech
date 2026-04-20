/**
 * Centralized API client for the POC Fintech backend.
 *
 * Uses fetch() with Bearer token authentication from Keycloak.
 * All requests include the JWT token for authorization.
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

/**
 * Returns authorization headers with the current Keycloak JWT.
 * Refreshes token if about to expire.
 */
async function getAuthHeaders(): Promise<Record<string, string>> {
  if (keycloak.authenticated) {
    // Refresh token if it expires within 30 seconds
    try {
      await keycloak.updateToken(30);
    } catch {
      keycloak.login();
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
 * Generic fetch wrapper with error handling and JWT auth.
 * Throws {@link ApiError} on non-2xx responses.
 */
async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const authHeaders = await getAuthHeaders();
  const res = await fetch(`${BASE}${url}`, {
    ...init,
    headers: {
      ...authHeaders,
      ...init?.headers,
    },
  });

  if (res.status === 401) {
    // Token expired or invalid — redirect to login
    keycloak.login();
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
