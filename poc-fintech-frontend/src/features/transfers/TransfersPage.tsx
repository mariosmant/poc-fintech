import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useAccountsList, useInitiateTransfer, useMonitoredTransfers, useTransfersList } from '../../hooks/useApi';
import { ErrorMessage, Spinner } from '../../components/ui/Feedback';
import { IbanDisplay } from '../../components/ui/IbanDisplay';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { formatCurrency, generateIdempotencyKey } from '../../utils/format';
import { formatIban, isValidIban, normalizeIban } from '../../utils/iban';
import type { Currency, InitiateTransferRequest, Transfer } from '../../types/api';

const CURRENCIES: Currency[] = ['USD', 'EUR', 'GBP', 'JPY', 'CHF'];

type TargetMode = 'internal' | 'external';

/** Returns true when the raw user-typed IBAN field contains no meaningful characters. */
function externalIbanInputIsEmpty(raw: string): boolean {
  return raw.replace(/\s+/g, '').length === 0;
}

/** Transfers page — initiate transfers and track their saga status. */
export function TransfersPage() {
  const [transferIds, setTransferIds] = useState<string[]>([]);
  const transfersListQuery = useTransfersList(50);
  const initiateMutation = useInitiateTransfer();
  const accountsQuery = useAccountsList();

  // Form state
  const [sourceId, setSourceId] = useState('');
  const [targetMode, setTargetMode] = useState<TargetMode>('internal');
  const [targetInternalId, setTargetInternalId] = useState('');
  const [targetIbanInput, setTargetIbanInput] = useState('');
  const [amount, setAmount] = useState('100');
  const [srcCurrency, setSrcCurrency] = useState<Currency>('USD');
  const [tgtCurrency, setTgtCurrency] = useState<Currency>('EUR');

  const ownAccounts = accountsQuery.data ?? [];
  const externalIbanNormalized = normalizeIban(targetIbanInput);
  const externalIbanValid = externalIbanInputIsEmpty(targetIbanInput) ? false : isValidIban(externalIbanNormalized);
  const externalIbanShowError = !externalIbanInputIsEmpty(targetIbanInput) && !externalIbanValid;

  // Track selected transfer details while all rows are monitored in the background.
  const [selectedTransferId, setSelectedTransferId] = useState('');
  const monitoredTransferIds = useMemo(() => {
    const latestIds = (transfersListQuery.data ?? []).map((transfer) => transfer.id);
    const merged = [...transferIds];
    for (const id of latestIds) {
      if (!merged.includes(id)) {
        merged.push(id);
      }
    }
    return merged;
  }, [transferIds, transfersListQuery.data]);

  const monitoredQueries = useMonitoredTransfers(monitoredTransferIds);

  const latestTransfersById = useMemo(
    () => new Map((transfersListQuery.data ?? []).map((transfer) => [transfer.id, transfer])),
    [transfersListQuery.data],
  );

  const transfers = useMemo(
    () => monitoredTransferIds
      .map((id, index) => monitoredQueries[index]?.data ?? latestTransfersById.get(id))
      .filter((transfer): transfer is Transfer => !!transfer),
    [latestTransfersById, monitoredQueries, monitoredTransferIds],
  );

  const selectedTransfer = useMemo(
    () => transfers.find((transfer) => transfer.id === selectedTransferId),
    [selectedTransferId, transfers],
  );

  useEffect(() => {
    const firstTransfer = transfers[0];
    if (!selectedTransferId && firstTransfer) {
      setSelectedTransferId(firstTransfer.id);
    }
  }, [selectedTransferId, transfers]);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const basePayload: InitiateTransferRequest = {
      sourceAccountId: sourceId,
      amount: parseFloat(amount),
      sourceCurrency: srcCurrency,
      targetCurrency: tgtCurrency,
      idempotencyKey: generateIdempotencyKey(),
    };
    const payload: InitiateTransferRequest = targetMode === 'internal'
      ? { ...basePayload, targetAccountId: targetInternalId }
      : { ...basePayload, targetIban: externalIbanNormalized };

    initiateMutation.mutate(payload, {
      onSuccess: (transfer) => {
        setTransferIds((prev) => (prev.includes(transfer.id) ? prev : [transfer.id, ...prev]));
        setSelectedTransferId(transfer.id);
      },
    });
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
              <label htmlFor="source-account" className="block text-sm font-medium text-gray-700 mb-1">Source Account</label>
              <select
                id="source-account"
                value={sourceId}
                onChange={(e) => {
                  setSourceId(e.target.value);
                  // Auto-set source currency from selected account
                  const acct = ownAccounts.find(a => a.id === e.target.value);
                  if (acct) setSrcCurrency(acct.currency);
                }}
                required
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500"
              >
                <option value="">Select your account…</option>
                {ownAccounts.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.ownerName} — {formatIban(a.iban)} — {formatCurrency(a.balance, a.currency)} {a.currency}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Beneficiary</label>
              <div className="inline-flex rounded-lg border border-gray-300 overflow-hidden mb-2" role="tablist" aria-label="Target account type">
                <button
                  type="button"
                  role="tab"
                  aria-selected={targetMode === 'internal'}
                  onClick={() => setTargetMode('internal')}
                  className={`px-3 py-1.5 text-sm ${targetMode === 'internal' ? 'bg-primary-600 text-white' : 'bg-white text-gray-700 hover:bg-gray-50'}`}
                >
                  My accounts
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={targetMode === 'external'}
                  onClick={() => setTargetMode('external')}
                  className={`px-3 py-1.5 text-sm border-l border-gray-300 ${targetMode === 'external' ? 'bg-primary-600 text-white' : 'bg-white text-gray-700 hover:bg-gray-50'}`}
                >
                  External IBAN
                </button>
              </div>
              {targetMode === 'internal' ? (
                <select
                  aria-label="Target account"
                  value={targetInternalId}
                  onChange={(e) => setTargetInternalId(e.target.value)}
                  required
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500"
                >
                  <option value="">Select beneficiary account…</option>
                  {ownAccounts
                    .filter((a) => a.id !== sourceId)
                    .map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.ownerName} — {formatIban(a.iban)} — {a.currency}
                      </option>
                    ))}
                </select>
              ) : (
                <>
                  <input
                    aria-label="External beneficiary IBAN"
                    type="text"
                    value={targetIbanInput}
                    onChange={(e) => setTargetIbanInput(e.target.value.toUpperCase())}
                    required
                    placeholder="e.g. DE89 5001 0517 0000 1234 56"
                    className={`w-full px-3 py-2 border rounded-lg text-sm font-mono focus:ring-2 focus:ring-primary-500 ${externalIbanShowError ? 'border-red-400' : 'border-gray-300'}`}
                  />
                  {externalIbanShowError && (
                    <p className="mt-1 text-xs text-red-600">Invalid IBAN — check country code, length and check digits.</p>
                  )}
                  {externalIbanValid && (
                    <p className="mt-1 text-xs text-green-600">✓ Valid IBAN ({formatIban(externalIbanNormalized)})</p>
                  )}
                </>
              )}
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
            disabled={
              initiateMutation.isPending ||
              !sourceId ||
              (targetMode === 'internal' ? !targetInternalId : !externalIbanValid)
            }
            className="px-6 py-2 bg-primary-600 text-white rounded-lg text-sm font-medium hover:bg-primary-700 disabled:opacity-50"
          >
            {initiateMutation.isPending ? 'Initiating…' : 'Initiate Transfer'}
          </button>
        </form>
        {initiateMutation.isError && (
          <div className="mt-3"><ErrorMessage message={initiateMutation.error.message} /></div>
        )}
      </div>

      {transfersListQuery.isError && !initiateMutation.isError && (
        <div className="mb-3"><ErrorMessage message={transfersListQuery.error.message} /></div>
      )}

      {/* Live monitored status for the selected transfer */}
      {selectedTransfer && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
          <h3 className="text-lg font-semibold mb-3">Live Transfer Status</h3>
          <div className="flex items-center gap-4">
            <StatusBadge status={selectedTransfer.status} />
            <span className="text-sm text-gray-500 font-mono">{selectedTransfer.id}</span>
            {selectedTransfer.exchangeRate && (
              <span className="text-sm text-gray-600">
                FX Rate: {selectedTransfer.exchangeRate.toFixed(6)}
              </span>
            )}
            {selectedTransfer.targetAmount != null && (
              <span className="text-sm font-medium">
                to {formatCurrency(selectedTransfer.targetAmount, selectedTransfer.targetCurrency)}
              </span>
            )}
          </div>
          {selectedTransfer.failureReason && (
            <p className="mt-2 text-sm text-red-600">Reason: {selectedTransfer.failureReason}</p>
          )}
          <p className="mt-2 text-xs text-gray-500">
            Active transfers auto-refresh every 2s until they reach a terminal state.
          </p>
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
                  onClick={() => setSelectedTransferId(t.id)}
                >
                  <td className="px-4 py-3 font-mono text-xs text-gray-500 break-all">{t.id}</td>
                  <td className="px-4 py-3"><StatusBadge status={t.status} /></td>
                  <td className="px-4 py-3 text-right font-mono">
                    {formatCurrency(t.sourceAmount, t.sourceCurrency)}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">
                    {t.sourceCurrency} → {t.targetCurrency}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">
                    <div className="flex flex-col gap-1">
                      <IbanDisplay iban={t.sourceIban} label="Source IBAN" />
                      <span className="text-gray-400">↓</span>
                      <IbanDisplay iban={t.targetIban} label="Target IBAN" />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {transfers.length === 0 && !initiateMutation.isPending && !transfersListQuery.isLoading && (
        <p className="text-gray-400 text-sm">No transfers yet. Create accounts first, then initiate a transfer.</p>
      )}
      {(initiateMutation.isPending || transfersListQuery.isLoading) && <Spinner className="mt-4" />}
    </div>
  );
}

