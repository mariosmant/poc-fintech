/** Reusable React Query hooks for fintech API calls. */
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { accountsApi, transfersApi, ledgerApi } from '../api/client';
import type {
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

