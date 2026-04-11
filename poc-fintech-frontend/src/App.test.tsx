import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './App';

function renderApp(initialRoute = '/') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialRoute]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('App routing', () => {
  it('renders the dashboard page at /', () => {
    renderApp('/');
    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
  });

  it('renders the accounts page at /accounts', () => {
    renderApp('/accounts');
    expect(screen.getByRole('heading', { name: 'Accounts' })).toBeInTheDocument();
    expect(screen.getAllByText('Create Account').length).toBeGreaterThan(0);
  });

  it('renders the transfers page at /transfers', () => {
    renderApp('/transfers');
    expect(screen.getByRole('heading', { name: 'Transfers' })).toBeInTheDocument();
    expect(screen.getAllByText('Initiate Transfer').length).toBeGreaterThan(0);
  });

  it('renders the ledger page at /ledger', () => {
    renderApp('/ledger');
    expect(screen.getByRole('heading', { name: 'Double-Entry Ledger' })).toBeInTheDocument();
  });

  it('renders sidebar navigation links', () => {
    renderApp('/');
    expect(screen.getByText(/POC Fintech/)).toBeInTheDocument();
    expect(screen.getAllByRole('link').length).toBeGreaterThan(0);
  });
});

