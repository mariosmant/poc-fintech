import { useState, type FormEvent } from 'react';
import { useAccountsList, useCreateAccount } from '../../hooks/useApi';
import { ErrorMessage, Spinner } from '../../components/ui/Feedback';
import { IbanDisplay } from '../../components/ui/IbanDisplay';
import { formatCurrency } from '../../utils/format';
import type { Currency } from '../../types/api';

const CURRENCIES: Currency[] = ['USD', 'EUR', 'GBP', 'JPY', 'CHF'];

/** Accounts page — create accounts and view them. */
export function AccountsPage() {
  const accountsQuery = useAccountsList();
  const createMutation = useCreateAccount();

  const [currency, setCurrency] = useState<Currency>('USD');
  const [balance, setBalance] = useState('1000');

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    createMutation.mutate(
      { currency, initialBalance: parseFloat(balance) },
      {
        onSuccess: () => {
          setBalance('1000');
        },
      },
    );
  };

  const accounts = accountsQuery.data ?? [];

  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Accounts</h2>

      {/* Create Account Form */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
        <h3 className="text-lg font-semibold mb-4">Create Account</h3>
        <form onSubmit={handleSubmit} className="flex flex-wrap gap-4 items-end">
          <div className="w-32">
            <label className="block text-sm font-medium text-gray-700 mb-1">Currency</label>
            <select
              value={currency}
              onChange={(e) => setCurrency(e.target.value as Currency)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500"
            >
              {CURRENCIES.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>
          <div className="w-40">
            <label className="block text-sm font-medium text-gray-700 mb-1">Initial Balance</label>
            <input
              type="number"
              value={balance}
              onChange={(e) => setBalance(e.target.value)}
              min="0"
              step="0.01"
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <button
            type="submit"
            disabled={createMutation.isPending}
            className="px-6 py-2 bg-primary-600 text-white rounded-lg text-sm font-medium hover:bg-primary-700 disabled:opacity-50 transition-colors"
          >
            {createMutation.isPending ? 'Creating…' : 'Create Account'}
          </button>
        </form>
        {createMutation.isError && (
          <div className="mt-3">
            <ErrorMessage message={createMutation.error.message} />
          </div>
        )}
      </div>

      {accountsQuery.isError && !createMutation.isError && (
        <div className="mb-3"><ErrorMessage message={accountsQuery.error.message} /></div>
      )}

      {/* Accounts List */}
      {accounts.length > 0 && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-6 py-3 font-medium text-gray-500">IBAN</th>
                <th className="text-left px-6 py-3 font-medium text-gray-500">Owner</th>
                <th className="text-right px-6 py-3 font-medium text-gray-500">Balance</th>
                <th className="text-left px-6 py-3 font-medium text-gray-500">Currency</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {accounts.map((a) => (
                <tr key={a.id} className="hover:bg-gray-50" title={`Account UUID: ${a.id}`}>
                  <td className="px-6 py-3"><IbanDisplay iban={a.iban} /></td>
                  <td className="px-6 py-3 font-medium">{a.ownerName}</td>
                  <td className="px-6 py-3 text-right font-mono">{formatCurrency(a.balance, a.currency)}</td>
                  <td className="px-6 py-3">{a.currency}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {accounts.length === 0 && !createMutation.isPending && (
        <p className="text-gray-400 text-sm">No accounts created yet. Use the form above to create one.</p>
      )}
      {createMutation.isPending && <Spinner className="mt-4" />}
    </div>
  );
}

