import type { QuotePayload } from './quoteSchema'

/**
 * Successful quote calculation payload returned by the backend API.
 */
export interface QuoteResult {
  /** Unique transaction identifier (UUID v4) assigned to the quote. */
  quoteId: string
  /**
   * Commission rate as an unscaled decimal fraction (e.g. 0.0231 for 2.3100%).
   * Display layers format this value as a percentage.
   */
  commissionRate: number
  /**
   * Calculated monetary payout amount in USD currency units (rounded to 2 decimal places).
   */
  totalCommission: number
}

/**
 * Structured HTTP error representing non-2xx responses from the quote service.
 */
export class QuoteHttpError extends Error {
  /** HTTP response status code (e.g. 400, 502, 503). */
  status: number
  /** Human-readable explanation or contract error detail message. */
  detail: string
  /** Optional state identifier indicating circuit breaker status (e.g. 'CIRCUIT_OPEN'). */
  state?: string

  constructor(status: number, detail: string, state?: string) {
    super(detail)
    this.name = 'QuoteHttpError'
    this.status = status
    this.detail = detail
    this.state = state
  }
}

/**
 * Submits validated loan parameters to the backend quotation endpoint.
 *
 * @remarks
 * ATTENTION(design): Employs a same-origin relative path (`/api/v1/quotes`).
 * In containerized deployment, the local nginx reverse proxy routes `/api/*` to the Spring Boot
 * backend on port 8000. In local Vite development, `vite.config.ts` proxies `/api` identically.
 * This completely eliminates cross-origin CORS overhead and bypasses Vite build-time environment inlining.
 *
 * Swap alternative: If direct external API routing is ever required, substitute a configured base URL
 * (e.g. `import.meta.env.VITE_API_URL + '/api/v1/quotes'`).
 *
 * @param payload - validated loan amount, tenure in months, and risk band tier
 * @returns promise resolving to {@link QuoteResult} on HTTP 200
 * @throws {@link QuoteHttpError} on non-200 responses (HTTP 400, 502, 503)
 */
export async function requestQuote(payload: QuotePayload): Promise<QuoteResult> {
  const res = await fetch('/api/v1/quotes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })

  const body = await res.json().catch(() => ({}))

  if (!res.ok) {
    throw new QuoteHttpError(res.status, body.detail ?? 'Request failed.', body.state)
  }

  // Defensive validation: guard against 200 responses with non-JSON or missing fields (e.g. captive portals)
  if (
    typeof body !== 'object' ||
    body === null ||
    typeof body.quoteId !== 'string' ||
    typeof body.commissionRate !== 'number' ||
    typeof body.totalCommission !== 'number'
  ) {
    throw new QuoteHttpError(
      502,
      'Invalid response payload received from quotation service.',
      undefined
    )
  }

  return body as QuoteResult
}
