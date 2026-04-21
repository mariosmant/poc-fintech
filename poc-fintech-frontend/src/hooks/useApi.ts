/** Reusable React Query hooks for fintech API calls. */
import { useEffect, useMemo } from 'react';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { accountsApi, transfersApi, ledgerApi } from '../api/client';
import type {
  Account,
  CreateAccountRequest,
  InitiateTransferRequest,
  Transfer,
  TransferStatus,
} from '../types/api';

// ── Query keys ────────────────────────────────────────────────────────

export const queryKeys = {
  accountsList: ['accounts', 'list'] as const,
  account: (id: string) => ['account', id] as const,
  transfersList: (limit: number) => ['transfers', 'list', limit] as const,
  transfer: (id: string) => ['transfer', id] as const,
  ledgerRecent: (limit: number) => ['ledger', 'recent', limit] as const,
  ledgerByAccount: (id: string) => ['ledger', 'account', id] as const,
  ledgerByTransfer: (id: string) => ['ledger', 'transfer', id] as const,
};

const TERMINAL_TRANSFER_STATUSES = new Set<TransferStatus>(['COMPLETED', 'FAILED']);

function monitorIntervalMs(transfer?: Transfer) {
  if (!transfer) {
    return 2_000;
  }
  return TERMINAL_TRANSFER_STATUSES.has(transfer.status) ? false : 2_000;
}

// ── Account hooks ─────────────────────────────────────────────────────

export function useAccount(id: string) {
  return useQuery({
    queryKey: queryKeys.account(id),
    queryFn: () => accountsApi.getById(id),
    enabled: !!id,
  });
}

export function useAccountsList() {
  return useQuery({
    queryKey: queryKeys.accountsList,
    queryFn: () => accountsApi.list(),
    refetchOnMount: 'always',
  });
}

export function useCreateAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateAccountRequest) => accountsApi.create(data),
    onSuccess: (account) => {
      qc.setQueryData(queryKeys.account(account.id), account);
      qc.invalidateQueries({ queryKey: queryKeys.accountsList, exact: true });
    },
  });
}

// ── Transfer hooks ────────────────────────────────────────────────────

export function useTransfer(id: string) {
  return useQuery({
    queryKey: queryKeys.transfer(id),
    queryFn: () => transfersApi.getById(id),
    enabled: !!id,
    refetchOnMount: 'always',
    refetchInterval: (query) => monitorIntervalMs(query.state.data),
    refetchIntervalInBackground: true,
  });
}

export function useTransfersList(limit = 50) {
  return useQuery({
    queryKey: queryKeys.transfersList(limit),
    queryFn: () => transfersApi.list(limit),
    refetchOnMount: 'always',
  });
}

/** Polls all tracked transfers and stops polling once each transfer reaches a terminal state. */
export function useMonitoredTransfers(ids: string[]) {
  return useQueries({
    queries: ids.map((id) => ({
      queryKey: queryKeys.transfer(id),
      queryFn: () => transfersApi.getById(id),
      enabled: !!id,
      refetchInterval: (query: { state: { data?: Transfer } }) => monitorIntervalMs(query.state.data),
      refetchIntervalInBackground: true,
    })),
  });
}

export function useInitiateTransfer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: InitiateTransferRequest) => transfersApi.initiate(data),
    onSuccess: (transfer) => {
      qc.setQueryData(queryKeys.transfer(transfer.id), transfer);
      qc.invalidateQueries({ queryKey: queryKeys.transfer(transfer.id), exact: true });
      qc.invalidateQueries({ queryKey: ['transfers', 'list'] });
    },
  });
}

// ── Ledger hooks ──────────────────────────────────────────────────────

export function useLedgerByAccount(accountId: string) {
  return useQuery({
    queryKey: queryKeys.ledgerByAccount(accountId),
    queryFn: () => ledgerApi.getByAccount(accountId),
    enabled: !!accountId,
    refetchOnMount: 'always',
  });
}

export function useLedgerByTransfer(transferId: string) {
  return useQuery({
    queryKey: queryKeys.ledgerByTransfer(transferId),
    queryFn: () => ledgerApi.getByTransfer(transferId),
    enabled: !!transferId,
    refetchOnMount: 'always',
  });
}

export function useLedgerRecent(limit = 100) {
  return useQuery({
    queryKey: queryKeys.ledgerRecent(limit),
    queryFn: () => ledgerApi.recent(limit),
    refetchOnMount: 'always',
  });
}

// ── Ledger enrichment helpers ─────────────────────────────────────────

/**
 * Fetches {@link Account} records for every id in `ids`, sharing the React Query
 * cache with {@link useAccount}. Results are returned as a `Map<id, Account>`.
 *
 * The user's own accounts (already loaded by {@link useAccountsList}) are seeded
 * into the individual-query cache so they do not trigger redundant network calls;
 * accounts that belong to other users (e.g. the counter-party on a cross-user
 * transfer) are fetched lazily on demand. A missing/403 response simply leaves
 * the account absent from the map and the caller can fall back to the UUID.
 */
export function useAccountsLookup(ids: readonly string[]): Map<string, Account> {
  const qc = useQueryClient();
  const ownAccountsQuery = useAccountsList();

  // Seed the per-id cache so we do not refetch accounts we already loaded in bulk.
  useEffect(() => {
    for (const account of ownAccountsQuery.data ?? []) {
      qc.setQueryData(queryKeys.account(account.id), account);
    }
  }, [ownAccountsQuery.data, qc]);

  const uniqueIds = useMemo(() => Array.from(new Set(ids.filter(Boolean))), [ids]);

  const queries = useQueries({
    queries: uniqueIds.map((id) => ({
      queryKey: queryKeys.account(id),
      queryFn: () => accountsApi.getById(id),
      enabled: !!id,
      staleTime: 60_000,
      retry: false,
    })),
  });

  return useMemo(() => {
    const map = new Map<string, Account>();
    for (const account of ownAccountsQuery.data ?? []) {
      map.set(account.id, account);
    }
    uniqueIds.forEach((id, index) => {
      const data = queries[index]?.data;
      if (data) map.set(id, data);
    });
    return map;
  }, [ownAccountsQuery.data, queries, uniqueIds]);
}

/**
 * Fetches {@link Transfer} records for every id in `ids`, sharing the React Query
 * cache with {@link useTransfer}. Results are returned as a `Map<id, Transfer>`.
 */
export function useTransfersLookup(ids: readonly string[]): Map<string, Transfer> {
  const qc = useQueryClient();
  const listQuery = useTransfersList();

  useEffect(() => {
    for (const transfer of listQuery.data ?? []) {
      qc.setQueryData(queryKeys.transfer(transfer.id), transfer);
    }
  }, [listQuery.data, qc]);

  const uniqueIds = useMemo(() => Array.from(new Set(ids.filter(Boolean))), [ids]);

  const queries = useQueries({
    queries: uniqueIds.map((id) => ({
      queryKey: queryKeys.transfer(id),
      queryFn: () => transfersApi.getById(id),
      enabled: !!id,
      staleTime: 60_000,
      retry: false,
    })),
  });

  return useMemo(() => {
    const map = new Map<string, Transfer>();
    for (const transfer of listQuery.data ?? []) {
      map.set(transfer.id, transfer);
    }
    uniqueIds.forEach((id, index) => {
      const data = queries[index]?.data;
      if (data) map.set(id, data);
    });
    return map;
  }, [listQuery.data, queries, uniqueIds]);
}

