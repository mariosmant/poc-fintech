/**
 * Domain types matching the backend API DTOs.
 * Single source of truth for the frontend data model.
 */

/** ISO 4217 currency codes supported by the backend. */
export type Currency = 'USD' | 'EUR' | 'GBP' | 'JPY' | 'CHF';

/** Transfer lifecycle states matching TransferStatus enum on backend. */
export type TransferStatus =
  | 'INITIATED'
  | 'FRAUD_CHECKING'
  | 'FX_CONVERTING'
  | 'DEBITING'
  | 'CREDITING'
  | 'RECORDING_LEDGER'
  | 'COMPLETED'
  | 'FAILED'
  | 'COMPENSATING';

/** Account response DTO. */
export interface Account {
  id: string;
  ownerName: string;
  balance: number;
  currency: Currency;
}

/** Transfer response DTO. */
export interface Transfer {
  id: string;
  status: TransferStatus;
  sourceAccountId: string;
  targetAccountId: string;
  sourceAmount: number;
  sourceCurrency: Currency;
  targetAmount: number | null;
  targetCurrency: Currency;
  exchangeRate: number | null;
  failureReason: string | null;
  idempotencyKey: string;
}

/** Ledger entry response DTO. */
export interface LedgerEntry {
  id: string;
  debitAccountId: string;
  creditAccountId: string;
  amount: number;
  currency: Currency;
  transferId: string;
  createdAt: string;
}

/** Request: create an account. */
export interface CreateAccountRequest {
  ownerName: string;
  currency: Currency;
  initialBalance: number;
}

/** Request: initiate a transfer. */
export interface InitiateTransferRequest {
  sourceAccountId: string;
  targetAccountId: string;
  amount: number;
  sourceCurrency: Currency;
  targetCurrency: Currency;
  idempotencyKey: string;
}

/** RFC 7807 Problem Detail response. */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  timestamp: string;
  errors?: string[];
}

