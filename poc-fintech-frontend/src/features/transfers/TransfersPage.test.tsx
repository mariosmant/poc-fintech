import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TransfersPage } from './TransfersPage';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <TransfersPage />
    </QueryClientProvider>,
  );
}

describe('TransfersPage monitoring', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('polls transfer status and updates the UI until completion', async () => {
    let transferReads = 0;

    mockFetch.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? 'GET';

      if (url.endsWith('/api/v1/transfers') && method === 'POST') {
        return {
          ok: true,
          status: 201,
          json: async () => ({
            id: 'tx-1',
            status: 'INITIATED',
            sourceAccountId: '11111111-1111-1111-1111-111111111111',
            targetAccountId: '22222222-2222-2222-2222-222222222222',
            sourceAmount: 100,
            sourceCurrency: 'USD',
            targetAmount: null,
            targetCurrency: 'EUR',
            exchangeRate: null,
            failureReason: null,
            idempotencyKey: 'key-1',
          }),
        };
      }

      if (url.includes('/api/v1/transfers?limit=') && method === 'GET') {
        return {
          ok: true,
          status: 200,
          json: async () => [],
        };
      }

      if (url.endsWith('/api/v1/accounts') && method === 'GET') {
        return {
          ok: true,
          status: 200,
          json: async () => [
            {
              id: '11111111-1111-1111-1111-111111111111',
              iban: 'DE89500105170000123456',
              ownerName: 'alice',
              ownerId: 'user-1',
              currency: 'USD',
              balance: 5000,
              createdAt: '2026-04-20T00:00:00Z',
              version: 0,
            },
            {
              id: '22222222-2222-2222-2222-222222222222',
              iban: 'DE02500105170648489890',
              ownerName: 'alice',
              ownerId: 'user-1',
              currency: 'EUR',
              balance: 2500,
              createdAt: '2026-04-20T00:00:00Z',
              version: 0,
            },
          ],
        };
      }

      if (url.endsWith('/api/v1/transfers/tx-1') && method === 'GET') {
        transferReads += 1;
        return {
          ok: true,
          status: 200,
          json: async () => ({
            id: 'tx-1',
            status: transferReads >= 2 ? 'COMPLETED' : 'INITIATED',
            sourceAccountId: '11111111-1111-1111-1111-111111111111',
            targetAccountId: '22222222-2222-2222-2222-222222222222',
            sourceAmount: 100,
            sourceCurrency: 'USD',
            targetAmount: transferReads >= 2 ? 92 : null,
            targetCurrency: 'EUR',
            exchangeRate: transferReads >= 2 ? 0.92 : null,
            failureReason: null,
            idempotencyKey: 'key-1',
          }),
        };
      }

      throw new Error(`Unexpected request: ${method} ${url}`);
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getAllByText(/alice/).length).toBeGreaterThan(0);
    });

    const comboboxes = screen.getAllByRole('combobox');
    // Source account dropdown
    fireEvent.change(comboboxes[0], {
      target: { value: '11111111-1111-1111-1111-111111111111' },
    });
    // Target account dropdown (internal mode is the default) — select the second account
    fireEvent.change(screen.getByLabelText('Target account'), {
      target: { value: '22222222-2222-2222-2222-222222222222' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Initiate Transfer' }));

    await waitFor(() => {
      expect(screen.getByText('Live Transfer Status')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getAllByText('Completed').length).toBeGreaterThan(0);
    }, { timeout: 7_000 });

    expect(transferReads).toBeGreaterThanOrEqual(2);
  });
});



