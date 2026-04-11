import { describe, it, expect, vi, beforeEach } from 'vitest';
import { accountsApi, transfersApi, ledgerApi, ApiError } from './client';

// Mock global fetch
const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

beforeEach(() => {
  mockFetch.mockReset();
});

describe('accountsApi', () => {
  it('create() posts to /api/v1/accounts and returns Account', async () => {
    const mockAccount = { id: 'abc-123', ownerName: 'Alice', balance: 1000, currency: 'USD' };
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: () => Promise.resolve(mockAccount),
    });

    const result = await accountsApi.create({
      ownerName: 'Alice',
      currency: 'USD',
      initialBalance: 1000,
    });

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/accounts',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(result).toEqual(mockAccount);
  });

  it('getById() fetches /api/v1/accounts/:id', async () => {
    const mockAccount = { id: 'abc-123', ownerName: 'Bob', balance: 500, currency: 'EUR' };
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockAccount),
    });

    const result = await accountsApi.getById('abc-123');
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/accounts/abc-123', expect.anything());
    expect(result).toEqual(mockAccount);
  });

  it('throws ApiError on non-2xx response', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 404,
      statusText: 'Not Found',
      json: () =>
        Promise.resolve({
          type: 'urn:fintech:error:not-found',
          title: 'Not Found',
          status: 404,
          detail: 'Account not found',
          timestamp: '2026-01-01T00:00:00Z',
        }),
    });

    await expect(accountsApi.getById('missing')).rejects.toThrow(ApiError);
  });
});

describe('transfersApi', () => {
  it('initiate() posts to /api/v1/transfers', async () => {
    const mockTransfer = {
      id: 'tx-1',
      status: 'INITIATED',
      sourceAccountId: 'a1',
      targetAccountId: 'a2',
      sourceAmount: 100,
      sourceCurrency: 'USD',
      targetAmount: null,
      targetCurrency: 'EUR',
      exchangeRate: null,
      failureReason: null,
      idempotencyKey: 'key-1',
    };
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: () => Promise.resolve(mockTransfer),
    });

    const result = await transfersApi.initiate({
      sourceAccountId: 'a1',
      targetAccountId: 'a2',
      amount: 100,
      sourceCurrency: 'USD',
      targetCurrency: 'EUR',
      idempotencyKey: 'key-1',
    });

    expect(result.status).toBe('INITIATED');
    expect(result.id).toBe('tx-1');
  });
});

describe('ledgerApi', () => {
  it('getByAccount() fetches /api/v1/ledger/account/:id', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve([]),
    });

    const result = await ledgerApi.getByAccount('acc-1');
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/ledger/account/acc-1', expect.anything());
    expect(result).toEqual([]);
  });
});

