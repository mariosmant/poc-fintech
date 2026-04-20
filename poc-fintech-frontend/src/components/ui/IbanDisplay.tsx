import { useState } from 'react';
import { formatIban, normalizeIban } from '../../utils/iban';

/**
 * Renders an IBAN grouped in blocks of 4 with a copy-to-clipboard action.
 * Banking UIs consistently present IBANs this way — it improves readability
 * and reduces transcription errors for customers reading out the code.
 */
export function IbanDisplay({ iban, label }: { iban: string | null | undefined; label?: string }) {
  const [copied, setCopied] = useState(false);
  if (!iban) return <span className="text-gray-400 text-xs italic">IBAN pending…</span>;

  const normalized = normalizeIban(iban);
  const pretty = formatIban(normalized);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(normalized);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard may be unavailable (e.g. test env) — silently ignore */
    }
  };

  return (
    <span className="inline-flex items-center gap-2">
      <span
        className="font-mono tracking-wider text-sm text-gray-800"
        aria-label={label ?? 'IBAN'}
        title={normalized}
      >
        {pretty}
      </span>
      <button
        type="button"
        onClick={handleCopy}
        className="text-xs text-primary-600 hover:text-primary-700 underline-offset-2 hover:underline"
        aria-label="Copy IBAN"
      >
        {copied ? 'Copied' : 'Copy'}
      </button>
    </span>
  );
}

