import { useMemo, useState, type FormEvent } from 'react';
import {
  useAccountsLookup,
  useLedgerByAccount,
  useLedgerByTransfer,
  useLedgerRecent,
  useTransfersLookup,
} from '../../hooks/useApi';
import { ErrorMessage, Spinner } from '../../components/ui/Feedback';
import { IbanDisplay } from '../../components/ui/IbanDisplay';
import { formatCurrency, formatDate } from '../../utils/format';
import type { Account, Transfer } from '../../types/api';

/** Renders the IBAN (grouped) + owner name for an account, with UUID fallback. */
function AccountCell({ id, account, tone }: { id: string; account?: Account; tone: 'debit' | 'credit' }) {
  const toneClass = tone === 'debit' ? 'text-red-600' : 'text-green-600';
  if (!account) {
    return (
      <div className="flex flex-col gap-0.5">
        <span className={`font-mono text-xs ${toneClass} break-all`}>{id}</span>
        <span className="text-[11px] text-gray-400">Account details unavailable</span>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-0.5">
      <IbanDisplay iban={account.iban} label={tone === 'debit' ? 'Debit IBAN' : 'Credit IBAN'} />
      <span className="text-xs text-gray-700">{account.ownerName}</span>
    </div>
  );
}

/** Renders transfer source→target IBAN pair with UUID fallback. */
function TransferCell({ id, transfer }: { id: string; transfer?: Transfer }) {
  if (!transfer) {
    return (
      <div className="flex flex-col gap-0.5">
        <span className="font-mono text-xs text-gray-500 break-all">{id}</span>
        <span className="text-[11px] text-gray-400">Transfer details unavailable</span>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-0.5">
      <span className="font-mono text-[11px] text-gray-400 break-all">{id}</span>
      <IbanDisplay iban={transfer.sourceIban} label="Source IBAN" />
      <span className="text-gray-400 text-xs">↓</span>
      <IbanDisplay iban={transfer.targetIban} label="Target IBAN" />
    </div>
  );
}

/** Ledger page — query double-entry accounting entries enriched with IBAN, owner and transfer context. */
export function LedgerPage() {
  const [searchType, setSearchType] = useState<'account' | 'transfer'>('account');
  const [searchId, setSearchId] = useState('');
  const [activeId, setActiveId] = useState('');
  const [activeType, setActiveType] = useState<'account' | 'transfer'>('account');

  const recentQuery = useLedgerRecent(100);
  const accountQuery = useLedgerByAccount(activeType === 'account' ? activeId : '');
  const transferQuery = useLedgerByTransfer(activeType === 'transfer' ? activeId : '');
  const query = activeId
    ? (activeType === 'account' ? accountQuery : transferQuery)
    : recentQuery;

  const entries = query.data ?? [];

  // Collect unique account / transfer ids referenced by the visible entries and
  // enrich them with IBAN + owner + transfer IBANs. Lookup is shared via the
  // React Query cache, so re-visits are free.
  const accountIds = useMemo(() => {
    const ids = new Set<string>();
    for (const e of entries) {
      if (e.debitAccountId) ids.add(e.debitAccountId);
      if (e.creditAccountId) ids.add(e.creditAccountId);
    }
    return Array.from(ids);
  }, [entries]);

  const transferIds = useMemo(() => {
    const ids = new Set<string>();
    for (const e of entries) if (e.transferId) ids.add(e.transferId);
    return Array.from(ids);
  }, [entries]);

  const accountsById = useAccountsLookup(accountIds);
  const transfersById = useTransfersLookup(transferIds);

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

      {!activeId && query.data && (
        <p className="text-xs text-gray-500 mb-3">Showing latest ledger entries from the backend.</p>
      )}

      {entries.length > 0 && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-200 bg-gray-50">
            <h3 className="text-sm font-semibold text-gray-700">
              {entries.length} ledger {entries.length === 1 ? 'entry' : 'entries'} found
            </h3>
          </div>
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Debit (IBAN / Owner)</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Credit (IBAN / Owner)</th>
                <th className="text-right px-4 py-3 font-medium text-gray-500">Amount</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Transfer</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {entries.map((entry) => (
                <tr key={entry.id} className="hover:bg-gray-50 align-top">
                  <td className="px-4 py-3">
                    <AccountCell
                      id={entry.debitAccountId}
                      account={accountsById.get(entry.debitAccountId)}
                      tone="debit"
                    />
                  </td>
                  <td className="px-4 py-3">
                    <AccountCell
                      id={entry.creditAccountId}
                      account={accountsById.get(entry.creditAccountId)}
                      tone="credit"
                    />
                  </td>
                  <td className="px-4 py-3 text-right font-mono font-medium whitespace-nowrap">
                    {formatCurrency(entry.amount, entry.currency)}
                  </td>
                  <td className="px-4 py-3">
                    <TransferCell id={entry.transferId} transfer={transfersById.get(entry.transferId)} />
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">
                    {formatDate(entry.createdAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {query.data && entries.length === 0 && (
        <p className="text-gray-400 text-sm">No ledger entries found for this ID.</p>
      )}
    </div>
  );
}

