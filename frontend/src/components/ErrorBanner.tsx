import { useEffect, useRef, useState } from 'react'

interface Props {
  message: string
  circuitOpen: boolean
  /** Monotonic attempt sequence from the form — re-arms the debounce even when
   * two consecutive errors carry the SAME message (a [message]-only dep would
   * leave the retry button permanently enabled after a repeat failure). */
  attempt: number
  onRetry: () => void
}

// ATTENTION(design): spec §5 — distinct treatment for circuit-open vs other
// failures; Retry CTA debounced 2s so a frustrated user cannot hammer the API.
const RETRY_DEBOUNCE_MS = 2000

export default function ErrorBanner({ message, circuitOpen, attempt, onRetry }: Props) {
  const [retryDisabled, setRetryDisabled] = useState(true)
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => {
    setRetryDisabled(true)
    timer.current = window.setTimeout(() => setRetryDisabled(false), RETRY_DEBOUNCE_MS)
    return () => window.clearTimeout(timer.current)
  }, [message, attempt]) // attempt re-arms the debounce on identical consecutive errors

  return (
    <div role="alert"
      className={
        circuitOpen
          ? 'rounded-lg border border-orange-400 bg-orange-50 p-4 space-y-2'
          : 'rounded-lg border border-red-400 bg-red-50 p-4 space-y-2'
      }>
      <p className={circuitOpen ? 'text-orange-800' : 'text-red-700'}>{message}</p>
      <button type="button" onClick={onRetry} disabled={retryDisabled}
        className="rounded bg-slate-700 px-3 py-1 text-sm text-white disabled:bg-slate-400">
        Retry Quote
      </button>
    </div>
  )
}
