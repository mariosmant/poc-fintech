/**
 * Centralized API client for the POC Fintech backend.
 *
 * Uses fetch() with a base URL that is proxied by Vite in dev mode
 * and can be configured via env variable in production.
 *
 * All functions are typed end-to-end using the shared types from @/types/api.
 */
import type {
  Account,
  CreateAccountRequest,
  InitiateTransferRequest,
  LedgerEntry,
  ProblemDetail,
  Transfer,
} from '../types/api';

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
 * Generic fetch wrapper with error handling.
 * Throws {@link ApiError} on non-2xx responses.
 */
async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${url}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  });

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
};

// ── Ledger ────────────────────────────────────────────────────────────

export const ledgerApi = {
  /** Get ledger entries by account ID. */
  getByAccount: (accountId: string) =>
    request<LedgerEntry[]>(`/ledger/account/${accountId}`),

  /** Get ledger entries by transfer ID. */
  getByTransfer: (transferId: string) =>
    request<LedgerEntry[]>(`/ledger/transfer/${transferId}`),
};

