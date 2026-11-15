import { useRef, useState } from 'react'
import { quoteSchema, type QuotePayload } from '../api/quoteSchema'
import { requestQuote, QuoteHttpError, type QuoteResult } from '../api/quote'
import ErrorBanner from './ErrorBanner'
import ResultCard from './ResultCard'

/**
 * Record type mapping field names to their client-side validation error messages.
 */
type FieldErrors = Partial<Record<keyof QuotePayload, string>>

/**
 * Primary loan quote generation form interface.
 *
 * <p>Implements Functional Requirements FR-1, FR-2, and Section 5 UI degraded states:
 * <ul>
 *   <li><b>Dynamic Client Validation:</b> Validates inputs with {@link quoteSchema} prior to network dispatch.</li>
 *   <li><b>Submission Idempotency:</b> Disables all input fields and the submit button during in-flight requests,
 *       displaying an animated loading spinner (FR-2 double-submit guard).</li>
 *   <li><b>Degraded-State UX:</b> Differentiates validation errors from server outages, surfacing actionable
 *       retry banners and prominent circuit-open warnings.</li>
 * </ul>
 */
export default function QuoteForm() {
  const [loanAmount, setLoanAmount] = useState('')
  const [loanTermInMonths, setLoanTermInMonths] = useState('')
  const [riskBand, setRiskBand] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<QuoteResult | null>(null)
  const [banner, setBanner] = useState<{ message: string; circuitOpen: boolean; attempt: number } | null>(null)
  const attemptSeqRef = useRef(0)

  /**
   * Handles quote form submission and network orchestration.
   *
   * @param e - optional React form submission event
   */
  const submit = async (e?: React.FormEvent) => {
    e?.preventDefault()

    // Design Note: guard against concurrent double-click submissions
    if (submitting) return

    const parsed = quoteSchema.safeParse({
      loanAmount: Number(loanAmount),
      loanTermInMonths: Number(loanTermInMonths),
      riskBand,
    })

    if (!parsed.success) {
      const errs: FieldErrors = {}
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof QuotePayload
        if (!errs[key]) errs[key] = issue.message
      }
      setFieldErrors(errs)
      setBanner(null)
      return
    }

    setFieldErrors({})
    setBanner(null)
    setSubmitting(true)

    try {
      const quote = await requestQuote(parsed.data)
      setResult(quote)
    } catch (err) {
      setResult(null)
      if (err instanceof QuoteHttpError) {
        setBanner({
          message: err.detail,
          circuitOpen: err.state === 'CIRCUIT_OPEN',
          attempt: ++attemptSeqRef.current,
        })
      } else {
        setBanner({
          message: 'Request failed. Please try again.',
          circuitOpen: false,
          attempt: ++attemptSeqRef.current,
        })
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-md space-y-4 p-4">
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Commission Quote</h1>

      <form onSubmit={submit} className="space-y-4 rounded-lg border border-slate-300 bg-white p-5 shadow-sm" noValidate>
        <fieldset disabled={submitting} className="space-y-4">
          <div>
            <label htmlFor="loanAmount" className="block text-sm font-medium text-slate-700">
              Loan Amount ($)
            </label>
            <input
              id="loanAmount"
              type="number"
              step="0.01"
              value={loanAmount}
              onChange={(e) => setLoanAmount(e.target.value)}
              placeholder="e.g. 250000"
              className="mt-1 block w-full rounded border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              aria-describedby={fieldErrors.loanAmount ? 'loanAmount-error' : undefined}
            />
            {fieldErrors.loanAmount && (
              <p id="loanAmount-error" role="alert" className="mt-1 text-xs font-medium text-red-600">
                {fieldErrors.loanAmount}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="loanTermInMonths" className="block text-sm font-medium text-slate-700">
              Loan Term (months)
            </label>
            <input
              id="loanTermInMonths"
              type="number"
              value={loanTermInMonths}
              onChange={(e) => setLoanTermInMonths(e.target.value)}
              placeholder="e.g. 36"
              className="mt-1 block w-full rounded border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              aria-describedby={fieldErrors.loanTermInMonths ? 'loanTerm-error' : undefined}
            />
            {fieldErrors.loanTermInMonths && (
              <p id="loanTerm-error" role="alert" className="mt-1 text-xs font-medium text-red-600">
                {fieldErrors.loanTermInMonths}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="riskBand" className="block text-sm font-medium text-slate-700">
              Risk Band
            </label>
            <select
              id="riskBand"
              value={riskBand}
              onChange={(e) => setRiskBand(e.target.value)}
              className="mt-1 block w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              aria-describedby={fieldErrors.riskBand ? 'riskBand-error' : undefined}
            >
              <option value="">Select operational risk tier…</option>
              <option value="LOW">LOW — Standard baseline risk</option>
              <option value="MEDIUM">MEDIUM — Moderate risk profile</option>
              <option value="HIGH">HIGH — Elevated risk premium</option>
            </select>
            {fieldErrors.riskBand && (
              <p id="riskBand-error" role="alert" className="mt-1 text-xs font-medium text-red-600">
                {fieldErrors.riskBand}
              </p>
            )}
          </div>
        </fieldset>

        <button
          type="submit"
          disabled={submitting}
          className="flex w-full items-center justify-center rounded bg-blue-600 px-4 py-2.5 text-sm font-medium text-white shadow transition hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:bg-blue-300"
        >
          {submitting && (
            <span
              aria-hidden="true"
              className="mr-2 inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"
            />
          )}
          {submitting ? 'Calculating…' : 'Calculate Quote'}
        </button>
      </form>

      {banner && (
        <ErrorBanner
          message={banner.message}
          circuitOpen={banner.circuitOpen}
          attempt={banner.attempt}
          onRetry={() => {
            void submit()
          }}
        />
      )}

      {result && <ResultCard result={result} />}
    </div>
  )
}
