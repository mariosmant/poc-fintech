/**
 * ISO 13616 IBAN utilities for the UI.
 *
 * Keeps the frontend's IBAN handling aligned with the backend
 * {@code IbanUtil} — same normalisation rules, same mod-97 check —
 * so validation failures caught on the server are also caught locally
 * before submission, yielding a snappier banking-grade UX.
 */

const IBAN_SHAPE = /^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/;
const COUNTRY_LENGTHS: Record<string, number> = {
  DE: 22,
  GB: 22,
  FR: 27,
  ES: 24,
  IT: 27,
  NL: 18,
  CH: 21,
  AT: 20,
  BE: 16,
  IE: 22,
};

/** Strips whitespace and uppercases an IBAN for canonical comparison/storage. */
export function normalizeIban(iban: string): string {
  return iban.replace(/\s+/g, '').toUpperCase();
}

/**
 * Formats an IBAN for display in groups of 4 characters (banking best practice).
 * Example: DE89500105170000123456 → "DE89 5001 0517 0000 1234 56".
 */
export function formatIban(iban: string | null | undefined): string {
  if (!iban) return '';
  const normalized = normalizeIban(iban);
  return normalized.replace(/(.{4})/g, '$1 ').trim();
}

/** Masks an IBAN for compact display: first 4 + •• + last 4. */
export function maskIban(iban: string | null | undefined): string {
  if (!iban) return '';
  const normalized = normalizeIban(iban);
  if (normalized.length <= 8) return normalized;
  return `${normalized.slice(0, 4)} •••• •••• ${normalized.slice(-4)}`;
}

/** Validates an IBAN: shape, country-length (when known) and mod-97 check. */
export function isValidIban(iban: string | null | undefined): boolean {
  if (!iban) return false;
  const normalized = normalizeIban(iban);
  if (!IBAN_SHAPE.test(normalized)) return false;
  const country = normalized.slice(0, 2);
  const expectedLen = COUNTRY_LENGTHS[country];
  if (expectedLen != null && normalized.length !== expectedLen) return false;
  const rearranged = normalized.slice(4) + normalized.slice(0, 4);
  let numeric = '';
  for (const ch of rearranged) {
    if (ch >= '0' && ch <= '9') numeric += ch;
    else if (ch >= 'A' && ch <= 'Z') numeric += (ch.charCodeAt(0) - 55).toString();
    else return false;
  }
  // mod-97 using streaming BigInt-free algorithm (safe for any length).
  let remainder = 0;
  for (const digit of numeric) {
    remainder = (remainder * 10 + Number(digit)) % 97;
  }
  return remainder === 1;
}

