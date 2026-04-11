import { useState, type FormEvent } from 'react';
import { useLedgerByAccount, useLedgerByTransfer } from '../../hooks/useApi';
import { ErrorMessage, Spinner } from '../../components/ui/Feedback';
import { formatCurrency, formatDate } from '../../utils/format';

/** Ledger page — query double-entry accounting entries. */
export function LedgerPage() {
  const [searchType, setSearchType] = useState<'account' | 'transfer'>('account');
  const [searchId, setSearchId] = useState('');
  const [activeId, setActiveId] = useState('');
  const [activeType, setActiveType] = useState<'account' | 'transfer'>('account');

  const accountQuery = useLedgerByAccount(activeType === 'account' ? activeId : '');
  const transferQuery = useLedgerByTransfer(activeType === 'transfer' ? activeId : '');
  const query = activeType === 'account' ? accountQuery : transferQuery;

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setActiveType(searchType);
    setActiveId(searchId);
  };

  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Double-Entry Ledger</h2>

      {/* Search */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
        <h3 className="text-lg font-semibold mb-4">Query Ledger Entries</h3>
        <form onSubmit={handleSubmit} className="flex flex-wrap gap-4 items-end">
          <div className="w-40">
            <label className="block text-sm font-medium text-gray-700 mb-1">Search By</label>
            <select
              value={searchType}
              onChange={(e) => setSearchType(e.target.value as 'account' | 'transfer')}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
            >
              <option value="account">Account ID</option>
              <option value="transfer">Transfer ID</option>
            </select>
          </div>
          <div className="flex-1 min-w-[250px]">
            <label className="block text-sm font-medium text-gray-700 mb-1">UUID</label>
            <input
              type="text"
              value={searchId}
              onChange={(e) => setSearchId(e.target.value)}
              required
              placeholder="Enter UUID…"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <button
            type="submit"
            className="px-6 py-2 bg-primary-600 text-white rounded-lg text-sm font-medium hover:bg-primary-700"
          >
            Search
          </button>
        </form>
      </div>

      {/* Results */}
      {query.isLoading && <Spinner className="mt-4" />}
      {query.isError && <ErrorMessage message={query.error.message} />}

      {query.data && query.data.length > 0 && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-200 bg-gray-50">
            <h3 className="text-sm font-semibold text-gray-700">
              {query.data.length} ledger {query.data.length === 1 ? 'entry' : 'entries'} found
            </h3>
          </div>
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-500">ID</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Debit Account</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Credit Account</th>
                <th className="text-right px-4 py-3 font-medium text-gray-500">Amount</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Transfer</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {query.data.map((entry) => (
                <tr key={entry.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{entry.id.slice(0, 8)}…</td>
                  <td className="px-4 py-3 font-mono text-xs text-red-600">{entry.debitAccountId.slice(0, 8)}…</td>
                  <td className="px-4 py-3 font-mono text-xs text-green-600">{entry.creditAccountId.slice(0, 8)}…</td>
                  <td className="px-4 py-3 text-right font-mono font-medium">
                    {formatCurrency(entry.amount, entry.currency)}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{entry.transferId.slice(0, 8)}…</td>
                  <td className="px-4 py-3 text-xs text-gray-500">{formatDate(entry.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {query.data && query.data.length === 0 && (
        <p className="text-gray-400 text-sm">No ledger entries found for this ID.</p>
      )}
    </div>
  );
}

