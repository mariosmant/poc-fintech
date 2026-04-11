import { Routes, Route } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { AccountsPage } from './features/accounts/AccountsPage';
import { TransfersPage } from './features/transfers/TransfersPage';
import { LedgerPage } from './features/ledger/LedgerPage';

/** Top-level application component with client-side routing. */
export function App() {
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

