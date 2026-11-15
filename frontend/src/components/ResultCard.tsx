import type { QuoteResult } from '../api/quote'

/**
 * Properties for the {@link ResultCard} component.
 */
export interface ResultCardProps {
  /** The calculated quote result object returned by the backend service. */
  result: QuoteResult
}

/**
 * Visual card displaying the calculated quote outcome.
 *
 * @remarks
 * ATTENTION(design): The backend API emits `commissionRate` as a decimal fraction (e.g. `0.0231`).
 * This component performs display-only percentage conversion: multiplying by 100, formatting to 4
 * decimal places, and appending a `%` symbol (e.g. `2.3100%`).
 *
 * Monetary amounts are formatted using standard browser `Intl.NumberFormat` localized to USD currency.
 * No monetary arithmetic occurs in the client.
 */
export default function ResultCard({ result }: ResultCardProps) {
  const pct = (result.commissionRate * 100).toFixed(4) + '%'
  const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(
    result.totalCommission
  )

  return (
    <div
      data-testid="result-card"
      className="rounded-lg border border-emerald-300 bg-emerald-50 p-4 space-y-1"
      aria-label="Commission Quote Details"
    >
      <h2 className="font-semibold text-emerald-800 text-lg">Quote details</h2>
      <p className="text-sm text-slate-700">
        Quote ID: <span data-testid="quote-id" className="font-mono text-xs">{result.quoteId}</span>
      </p>
      <p className="text-sm text-slate-700">
        Commission Rate: <span data-testid="rate" className="font-semibold text-slate-900">{pct}</span>
      </p>
      <p className="text-sm text-slate-700">
        Total Commission: <span data-testid="total" className="font-semibold text-emerald-700 text-base">{currency}</span>
      </p>
    </div>
  )
}
