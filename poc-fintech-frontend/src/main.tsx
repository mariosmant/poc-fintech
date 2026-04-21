import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './auth/AuthProvider';
import { App } from './App';
import { ApiError } from './api/client';
import './index.css';

/**
 * React Query client with production-oriented defaults.
 *
 * Short staleness keeps monitoring screens fresh, while reconnect/focus
 * refetch handles laptop sleep/resume and network drops gracefully.
 * Authentication failures (401 / refresh failure) must NOT be retried —
 * the single-flight login guard will redirect to Keycloak, and retries
 * would otherwise stack identical redirects each carrying a new OAuth
 * `state` param.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5_000,
      gcTime: 5 * 60_000,
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status === 401) return false;
        if (error instanceof Error && /unauthorized|token refresh failed/i.test(error.message)) {
          return false;
        }
        return failureCount < 2;
      },
      refetchOnWindowFocus: true,
      refetchOnReconnect: true,
    },
  },
});

const root = document.getElementById('root');
if (!root) throw new Error('Root element not found');

createRoot(root).render(
  <StrictMode>
    <AuthProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </AuthProvider>
  </StrictMode>,
);
