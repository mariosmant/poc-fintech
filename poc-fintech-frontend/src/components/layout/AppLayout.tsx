import { type ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../auth/AuthProvider';

const navItems = [
  { to: '/', label: 'Dashboard' },
  { to: '/accounts', label: 'Accounts' },
  { to: '/transfers', label: 'Transfers' },
  { to: '/ledger', label: 'Ledger' },
] as const;

/** Main application layout with sidebar navigation and user context. */
export function AppLayout({ children }: { children: ReactNode }) {
  const { username, logout } = useAuth();

  return (
    <div className="flex min-h-screen">
      {/* Sidebar */}
      <aside className="w-64 bg-primary-900 text-white flex flex-col">
        <div className="p-6 border-b border-primary-700">
          <h1 className="text-xl font-bold tracking-tight">💰 POC Fintech</h1>
          <p className="text-xs text-primary-300 mt-1">Production Architecture Demo</p>
        </div>
        <nav className="flex-1 p-4 space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `block px-4 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-primary-700 text-white'
                    : 'text-primary-200 hover:bg-primary-800 hover:text-white'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        {/* User info + logout */}
        <div className="p-4 border-t border-primary-700">
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm text-primary-200 truncate">
              👤 {username ?? 'User'}
            </span>
            <button
              onClick={logout}
              className="text-xs text-primary-400 hover:text-white transition-colors"
              title="Sign out"
            >
              Logout
            </button>
          </div>
          <p className="text-xs text-primary-400">Hexagonal • DDD • CQRS</p>
          <p className="text-xs text-primary-400">Saga • Outbox • Kafka</p>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 bg-gray-50">
        <div className="p-8">{children}</div>
      </main>
    </div>
  );
}
