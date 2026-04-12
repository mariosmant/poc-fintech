import { useEffect } from 'react';
import { Routes, Route, useLocation } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { queryKeys } from './hooks/useApi';
import { AppLayout } from './components/layout/AppLayout';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { AccountsPage } from './features/accounts/AccountsPage';
import { TransfersPage } from './features/transfers/TransfersPage';
import { LedgerPage } from './features/ledger/LedgerPage';

/** Top-level application component with client-side routing. */
export function App() {
  const location = useLocation();
  const queryClient = useQueryClient();

  useEffect(() => {
    // Force fresh monitoring data whenever user navigates between pages.
    queryClient.invalidateQueries({ queryKey: queryKeys.accountsList, exact: true });
    queryClient.invalidateQueries({ queryKey: ['transfers', 'list'] });
    queryClient.invalidateQueries({ queryKey: ['ledger'] });
  }, [location.pathname, queryClient]);

  return (
    <AppLayout>
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/accounts" element={<AccountsPage />} />
        <Route path="/transfers" element={<TransfersPage />} />
        <Route path="/ledger" element={<LedgerPage />} />
      </Routes>
    </AppLayout>
  );
}
