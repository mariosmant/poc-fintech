/**
 * Utility: generate a UUID v4 for idempotency keys.
 * Uses crypto.randomUUID() (Web Crypto API — browser-native, CSPRNG).
 */
export function generateIdempotencyKey(): string {
  return crypto.randomUUID();
}

/** Formats a number as currency string. */
export function formatCurrency(amount: number, currency: string): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: currency === 'JPY' ? 0 : 2,
    maximumFractionDigits: currency === 'JPY' ? 0 : 2,
  }).format(amount);
}

/** Formats an ISO date string to locale. */
export function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

