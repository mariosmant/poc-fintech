import type { TransferStatus } from '../../types/api';

const statusConfig: Record<TransferStatus, { bg: string; text: string; label: string }> = {
  INITIATED: { bg: 'bg-blue-100', text: 'text-blue-800', label: 'Initiated' },
  FRAUD_CHECKING: { bg: 'bg-yellow-100', text: 'text-yellow-800', label: 'Fraud Check' },
  FX_CONVERTING: { bg: 'bg-purple-100', text: 'text-purple-800', label: 'FX Converting' },
  DEBITING: { bg: 'bg-orange-100', text: 'text-orange-800', label: 'Debiting' },
  CREDITING: { bg: 'bg-orange-100', text: 'text-orange-800', label: 'Crediting' },
  RECORDING_LEDGER: { bg: 'bg-indigo-100', text: 'text-indigo-800', label: 'Recording' },
  COMPLETED: { bg: 'bg-green-100', text: 'text-green-800', label: 'Completed' },
  FAILED: { bg: 'bg-red-100', text: 'text-red-800', label: 'Failed' },
  COMPENSATING: { bg: 'bg-red-100', text: 'text-red-800', label: 'Compensating' },
};

/** Renders a coloured badge for a transfer status. */
export function StatusBadge({ status }: { status: TransferStatus }) {
  const cfg = statusConfig[status] ?? statusConfig.INITIATED;
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${cfg.bg} ${cfg.text}`}>
      {cfg.label}
    </span>
  );
}

