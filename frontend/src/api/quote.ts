import type { QuotePayload } from './quoteSchema'

export interface QuoteResult {
  quoteId: string
  commissionRate: number
  totalCommission: number
}

export class QuoteHttpError extends Error {
  status: number
  detail: string
  state?: string
  constructor(status: number, detail: string, state?: string) {
    super(detail)
    this.status = status
    this.detail = detail
    this.state = state
  }
}

// ATTENTION(design): same-origin relative URL — nginx proxies /api/* to the
// backend (see frontend/nginx.conf). Swap variant: absolute URL from an env
// var (import.meta.env.VITE_API_URL) if you ever drop the proxy.
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
  return body as QuoteResult
}
