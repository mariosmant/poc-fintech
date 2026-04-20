import { describe, expect, it } from 'vitest';
import { formatIban, isValidIban, maskIban, normalizeIban } from './iban';

describe('iban utils', () => {
  it('normalizes whitespace and case', () => {
    expect(normalizeIban(' de89 5001 0517 0000 1234 56 ')).toBe('DE89500105170000123456');
  });

  it('formats into groups of 4', () => {
    expect(formatIban('DE89500105170000123456')).toBe('DE89 5001 0517 0000 1234 56');
  });

  it('masks for compact display', () => {
    expect(maskIban('DE89500105170000123456')).toBe('DE89 •••• •••• 3456');
  });

  it('validates the published Deutsche Bank test IBAN', () => {
    expect(isValidIban('DE89 3704 0044 0532 0130 00')).toBe(true);
  });

  it('rejects an IBAN with a broken check digit', () => {
    expect(isValidIban('DE00 3704 0044 0532 0130 00')).toBe(false);
  });

  it('rejects an IBAN with wrong country length', () => {
    expect(isValidIban('DE89 3704 0044 0532 0130')).toBe(false);
  });

  it('rejects rubbish input', () => {
    expect(isValidIban('not-an-iban')).toBe(false);
    expect(isValidIban('')).toBe(false);
    expect(isValidIban(null)).toBe(false);
  });
});

