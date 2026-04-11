/** Reusable React Query hooks for fintech API calls. */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { accountsApi, transfersApi, ledgerApi } from '../api/client';
import type { CreateAccountRequest, InitiateTransferRequest } from '../types/api';

// ── Query keys ────────────────────────────────────────────────────────

export const queryKeys = {
  account: (id: string) => ['account', id] as const,
  transfer: (id: string) => ['transfer', id] as const,
  ledgerByAccount: (id: string) => ['ledger', 'account', id] as const,
  ledgerByTransfer: (id: string) => ['ledger', 'transfer', id] as const,
};

// ── Account hooks ─────────────────────────────────────────────────────

export function useAccount(id: string) {
  return useQuery({
    queryKey: queryKeys.account(id),
    queryFn: () => accountsApi.getById(id),
    enabled: !!id,
  });
}

export function useCreateAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateAccountRequest) => accountsApi.create(data),
    onSuccess: (account) => {
      qc.setQueryData(queryKeys.account(account.id), account);
    },
  });
}

// ── Transfer hooks ────────────────────────────────────────────────────

export function useTransfer(id: string) {
  return useQuery({
    queryKey: queryKeys.transfer(id),
    queryFn: () => transfersApi.getById(id),
    enabled: !!id,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      // Auto-refresh while transfer is in-progress
      return status && status !== 'COMPLETED' && status !== 'FAILED' ? 2000 : false;
    },
  });
}

export function useInitiateTransfer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: InitiateTransferRequest) => transfersApi.initiate(data),
    onSuccess: (transfer) => {
      qc.setQueryData(queryKeys.transfer(transfer.id), transfer);
    },
  });
}

// ── Ledger hooks ──────────────────────────────────────────────────────

export function useLedgerByAccount(accountId: string) {
  return useQuery({
    queryKey: queryKeys.ledgerByAccount(accountId),
    queryFn: () => ledgerApi.getByAccount(accountId),
    enabled: !!accountId,
  });
}

export function useLedgerByTransfer(transferId: string) {
  return useQuery({
    queryKey: queryKeys.ledgerByTransfer(transferId),
    queryFn: () => ledgerApi.getByTransfer(transferId),
    enabled: !!transferId,
  });
}

