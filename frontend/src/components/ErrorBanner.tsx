import { useEffect, useRef, useState } from 'react'

/**
 * Properties for the {@link ErrorBanner} component.
 */
export interface ErrorBannerProps {
  /** Explanatory failure reason or contract message returned by the server. */
  message: string
  /** Flag indicating whether the upstream CircuitBreaker is currently in the OPEN state. */
  circuitOpen: boolean
  /**
   * Monotonic submission attempt counter.
   *
   * @remarks
   * Passed from the parent form to guarantee that the 2000ms debounce timer re-arms even if
   * repeated network dispatches return the exact same error message string.
   */
  attempt: number
  /** Callback invoked when the user clicks the "Retry Quote" call-to-action button. */
  onRetry: () => void
}

/**
 * Client-side debounce interval in milliseconds applied to the Retry button.
 *
 * @remarks
 * ATTENTION(design): Enforces a 2-second rate limit on manual retries to prevent
 * frustrated users from hammering upstream services during degraded operations (spec §5).
 */
const RETRY_DEBOUNCE_MS = 2000

/**
 * Actionable degraded-state notification banner with debounced retry controls.
 *
 * @remarks
 * Displays distinct visual indicators depending on the failure mode:
 * <ul>
 *   <li>Orange warning card when {@code circuitOpen} is true, advising that requests are paused.</li>
 *   <li>Red alert card for transient partner outages and standard request errors.</li>
 * </ul>
 */
export default function ErrorBanner({ message, circuitOpen, attempt, onRetry }: ErrorBannerProps) {
  const [retryDisabled, setRetryDisabled] = useState(true)
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => {
    // Re-arm debounce lock whenever the error message or attempt counter changes
    setRetryDisabled(true)
    timer.current = window.setTimeout(() => setRetryDisabled(false), RETRY_DEBOUNCE_MS)
    return () => window.clearTimeout(timer.current)
  }, [message, attempt])

  return (
    <div
      role="alert"
      className={
        circuitOpen
          ? 'rounded-lg border border-orange-400 bg-orange-50 p-4 space-y-2'
          : 'rounded-lg border border-red-400 bg-red-50 p-4 space-y-2'
      }
    >
      <p className={circuitOpen ? 'text-orange-800 text-sm font-medium' : 'text-red-700 text-sm font-medium'}>
        {message}
      </p>
      <button
        type="button"
        onClick={onRetry}
        disabled={retryDisabled}
        className="rounded bg-slate-700 px-3 py-1.5 text-sm font-medium text-white shadow-sm transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
      >
        Retry Quote
      </button>
    </div>
  )
}
