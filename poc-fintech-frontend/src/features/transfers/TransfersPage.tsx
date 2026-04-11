import { useState, type FormEvent } from 'react';
import { useInitiateTransfer, useTransfer } from '../../hooks/useApi';
import { ErrorMessage, Spinner } from '../../components/ui/Feedback';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { formatCurrency, generateIdempotencyKey } from '../../utils/format';
import type { Currency, Transfer } from '../../types/api';

const CURRENCIES: Currency[] = ['USD', 'EUR', 'GBP', 'JPY', 'CHF'];

/** Transfers page — initiate transfers and track their saga status. */
export function TransfersPage() {
  const [transfers, setTransfers] = useState<Transfer[]>([]);
  const initiateMutation = useInitiateTransfer();

  // Form state
  const [sourceId, setSourceId] = useState('');
  const [targetId, setTargetId] = useState('');
  const [amount, setAmount] = useState('100');
  const [srcCurrency, setSrcCurrency] = useState<Currency>('USD');
  const [tgtCurrency, setTgtCurrency] = useState<Currency>('EUR');

  // Polling for a specific transfer
  const [pollingId, setPollingId] = useState('');
  const pollingQuery = useTransfer(pollingId);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    initiateMutation.mutate(
      {
        sourceAccountId: sourceId,
        targetAccountId: targetId,
        amount: parseFloat(amount),
        sourceCurrency: srcCurrency,
        targetCurrency: tgtCurrency,
        idempotencyKey: generateIdempotencyKey(),
      },
      {
        onSuccess: (transfer) => {
          setTransfers((prev) => [transfer, ...prev]);
          setPollingId(transfer.id);
        },
      },
    );
  };

  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Transfers</h2>

      {/* Initiate Transfer Form */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
        <h3 className="text-lg font-semibold mb-4">Initiate Transfer</h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Source Account ID</label>
              <input
                type="text"
                value={sourceId}
                onChange={(e) => setSourceId(e.target.value)}
                required
                placeholder="UUID of source account"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Target Account ID</label>
              <input
                type="text"
                value={targetId}
                onChange={(e) => setTargetId(e.target.value)}
                required
                placeholder="UUID of target account"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500"
              />
            </div>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Amount</label>
              <input
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                min="0.01"
                step="0.01"
                required
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Source Currency</label>
              <select
                value={srcCurrency}
                onChange={(e) => setSrcCurrency(e.target.value as Currency)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
              >
                {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Target Currency</label>
              <select
                value={tgtCurrency}
                onChange={(e) => setTgtCurrency(e.target.value as Currency)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
              >
                {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
          </div>
          <button
            type="submit"
            disabled={initiateMutation.isPending}
            className="px-6 py-2 bg-primary-600 text-white rounded-lg text-sm font-medium hover:bg-primary-700 disabled:opacity-50"
          >
            {initiateMutation.isPending ? 'Initiating…' : 'Initiate Transfer'}
          </button>
        </form>
        {initiateMutation.isError && (
          <div className="mt-3"><ErrorMessage message={initiateMutation.error.message} /></div>
        )}
      </div>

      {/* Live Polling Status */}
      {pollingQuery.data && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
          <h3 className="text-lg font-semibold mb-3">📡 Live Transfer Status</h3>
          <div className="flex items-center gap-4">
            <StatusBadge status={pollingQuery.data.status} />
            <span className="text-sm text-gray-500 font-mono">{pollingQuery.data.id}</span>
            {pollingQuery.data.exchangeRate && (
              <span className="text-sm text-gray-600">
                FX Rate: {pollingQuery.data.exchangeRate.toFixed(6)}
              </span>
            )}
            {pollingQuery.data.targetAmount != null && (
              <span className="text-sm font-medium">
                → {formatCurrency(pollingQuery.data.targetAmount, pollingQuery.data.targetCurrency)}
              </span>
            )}
          </div>
          {pollingQuery.data.failureReason && (
            <p className="mt-2 text-sm text-red-600">Reason: {pollingQuery.data.failureReason}</p>
          )}
        </div>
      )}

      {/* Transfers Table */}
      {transfers.length > 0 && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-500">ID</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Status</th>
                <th className="text-right px-4 py-3 font-medium text-gray-500">Amount</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">FX</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Source → Target</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {transfers.map((t) => (
                <tr
                  key={t.id}
                  className="hover:bg-gray-50 cursor-pointer"
                  onClick={() => setPollingId(t.id)}
                >
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{t.id.slice(0, 8)}…</td>
                  <td className="px-4 py-3"><StatusBadge status={t.status} /></td>
                  <td className="px-4 py-3 text-right font-mono">
                    {formatCurrency(t.sourceAmount, t.sourceCurrency)}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">
                    {t.sourceCurrency} → {t.targetCurrency}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">
                    {t.sourceAccountId.slice(0, 8)}… → {t.targetAccountId.slice(0, 8)}…
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {transfers.length === 0 && !initiateMutation.isPending && (
        <p className="text-gray-400 text-sm">No transfers yet. Create accounts first, then initiate a transfer.</p>
      )}
      {initiateMutation.isPending && <Spinner className="mt-4" />}
    </div>
  );
}

