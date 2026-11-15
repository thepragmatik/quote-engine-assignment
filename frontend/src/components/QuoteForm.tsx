import { useState } from 'react'
import { quoteSchema, type QuotePayload } from '../api/quoteSchema'
import { requestQuote, QuoteHttpError, type QuoteResult } from '../api/quote'
import ErrorBanner from './ErrorBanner'
import ResultCard from './ResultCard'

type FieldErrors = Partial<Record<keyof QuotePayload, string>>

export default function QuoteForm() {
  const [loanAmount, setLoanAmount] = useState('')
  const [loanTermInMonths, setLoanTermInMonths] = useState('')
  const [riskBand, setRiskBand] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<QuoteResult | null>(null)
  const [banner, setBanner] = useState<{ message: string; circuitOpen: boolean } | null>(null)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (submitting) return // ATTENTION(design): double-submit guard
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
      setResult(await requestQuote(parsed.data))
    } catch (err) {
      setResult(null)
      if (err instanceof QuoteHttpError) {
        setBanner({ message: err.detail, circuitOpen: err.state === 'CIRCUIT_OPEN' })
      } else {
        setBanner({ message: 'Request failed. Please try again.', circuitOpen: false })
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-md space-y-4 p-4">
      <h1 className="text-2xl font-semibold">Commission Quote</h1>
      <form onSubmit={submit} className="space-y-3 rounded-lg border border-slate-300 p-4 bg-white" noValidate>
        <fieldset disabled={submitting} className="space-y-3">
          <div>
            <label htmlFor="loanAmount" className="block text-sm font-medium">Loan Amount ($)</label>
            <input id="loanAmount" type="number" step="0.01" value={loanAmount}
              onChange={(e) => setLoanAmount(e.target.value)}
              className="mt-1 w-full rounded border border-slate-400 px-2 py-1" />
            {fieldErrors.loanAmount && <p role="alert" className="text-sm text-red-600">{fieldErrors.loanAmount}</p>}
          </div>
          <div>
            <label htmlFor="loanTermInMonths" className="block text-sm font-medium">Loan Term (months)</label>
            <input id="loanTermInMonths" type="number" value={loanTermInMonths}
              onChange={(e) => setLoanTermInMonths(e.target.value)}
              className="mt-1 w-full rounded border border-slate-400 px-2 py-1" />
            {fieldErrors.loanTermInMonths && <p role="alert" className="text-sm text-red-600">{fieldErrors.loanTermInMonths}</p>}
          </div>
          <div>
            <label htmlFor="riskBand" className="block text-sm font-medium">Risk Band</label>
            <select id="riskBand" value={riskBand}
              onChange={(e) => setRiskBand(e.target.value)}
              className="mt-1 w-full rounded border border-slate-400 px-2 py-1">
              <option value="">Select…</option>
              <option value="LOW">LOW</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HIGH">HIGH</option>
            </select>
            {fieldErrors.riskBand && <p role="alert" className="text-sm text-red-600">{fieldErrors.riskBand}</p>}
          </div>
        </fieldset>
        <button type="submit" disabled={submitting}
          className="w-full rounded bg-blue-600 px-4 py-2 font-medium text-white disabled:bg-blue-300">
          {submitting ? 'Calculating…' : 'Calculate Quote'}
        </button>
      </form>
      {banner && <ErrorBanner message={banner.message} circuitOpen={banner.circuitOpen} onRetry={() => { void submit(new Event('retry') as unknown as React.FormEvent) }} />}
      {result && <ResultCard result={result} />}
    </div>
  )
}
