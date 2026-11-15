import type { QuoteResult } from '../api/quote'

// ATTENTION(design): the API emits commissionRate as a FRACTION (0.0231).
// Display-only conversion: × 100, 4dp, "%" (e.g. 2.3100%). No money math here.
export default function ResultCard({ result }: { result: QuoteResult }) {
  const pct = (result.commissionRate * 100).toFixed(4) + '%'
  const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
    .format(result.totalCommission)
  return (
    <div data-testid="result-card" className="rounded-lg border border-emerald-300 bg-emerald-50 p-4 space-y-1">
      <h2 className="font-semibold text-emerald-800">Quote details</h2>
      <p>Quote ID: <span data-testid="quote-id">{result.quoteId}</span></p>
      <p>Commission Rate: <span data-testid="rate">{pct}</span></p>
      <p>Total Commission: <span data-testid="total">{currency}</span></p>
    </div>
  )
}
