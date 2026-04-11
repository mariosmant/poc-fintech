import { describe, it, expect } from 'vitest';
import { formatCurrency, formatDate, generateIdempotencyKey } from './format';

describe('formatCurrency', () => {
  it('formats USD with 2 decimal places', () => {
    expect(formatCurrency(1234.5, 'USD')).toBe('$1,234.50');
  });

  it('formats EUR', () => {
    expect(formatCurrency(500, 'EUR')).toBe('€500.00');
  });

  it('formats JPY with 0 decimal places', () => {
    expect(formatCurrency(1500, 'JPY')).toBe('¥1,500');
  });

  it('formats large numbers with commas', () => {
    expect(formatCurrency(1000000, 'USD')).toBe('$1,000,000.00');
  });
});

describe('generateIdempotencyKey', () => {
  it('returns a valid UUID v4 string', () => {
    const key = generateIdempotencyKey();
    expect(key).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it('generates unique keys', () => {
    const keys = new Set(Array.from({ length: 100 }, () => generateIdempotencyKey()));
    expect(keys.size).toBe(100);
  });
});

describe('formatDate', () => {
  it('formats an ISO date string', () => {
    const result = formatDate('2026-01-15T10:30:00.000Z');
    expect(result).toBeTruthy();
    expect(typeof result).toBe('string');
  });
});

