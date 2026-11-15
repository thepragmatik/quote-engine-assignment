import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, act, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../App'

// ATTENTION(test): these tests assert the FR-1 contract strings LITERALLY —
// they are strict functional requirements, not arbitrary cosmetic copy.
const AMOUNT_ERR = 'Loan amount must be between $1,000 and $10,000,000.'
const TERM_ERR = 'Loan term must be between 6 and 360 months.'
const BAND_ERR = 'Please select a valid risk band.'

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/loan amount/i), '250000')
  await user.type(screen.getByLabelText(/loan term/i), '36')
  await user.selectOptions(screen.getByLabelText(/risk band/i), 'MEDIUM')
}

describe('QuoteForm — Comprehensive User Experience & Edge Cases', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn())
  })

  describe('Happy Path Quote Scenarios', () => {
    it('renders the result card with formatted percentage and USD currency on HTTP 200', async () => {
      const user = userEvent.setup()
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
        JSON.stringify({ quoteId: '123e4567-e89b-42d3-a456-426614174000', commissionRate: 0.0231, totalCommission: 5775 }),
        { status: 200, headers: { 'Content-Type': 'application/json' } })))

      render(<App />)
      await fillValid(user)
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      expect(await screen.findByText('2.3100%')).toBeInTheDocument()
      expect(screen.getByText('$5,775.00')).toBeInTheDocument()
      expect(screen.getByText('123e4567-e89b-42d3-a456-426614174000')).toBeInTheDocument()
    })

    it('displays loading spinner and disables submit button during in-flight network dispatch', async () => {
      const user = userEvent.setup()
      vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))) // never resolves

      render(<App />)
      await fillValid(user)
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      const btn = await screen.findByRole('button', { name: /calculating/i })
      expect(btn).toBeDisabled()
    })
  })

  describe('Client-Side Validation & Boundary Enforcement (Sad Cases)', () => {
    it('shows all three exact FR-1 error strings when submitting invalid/empty fields without dispatching fetch', async () => {
      const user = userEvent.setup()
      render(<App />)

      await user.type(screen.getByLabelText(/loan amount/i), '999.99')
      await user.type(screen.getByLabelText(/loan term/i), '5')
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      expect(await screen.findByText(AMOUNT_ERR)).toBeInTheDocument()
      expect(screen.getByText(TERM_ERR)).toBeInTheDocument()
      expect(screen.getByText(BAND_ERR)).toBeInTheDocument()
      expect(fetch).not.toHaveBeenCalled()
    })

    it('rejects loan amounts exceeding the upper boundary ($10,000,000.01)', async () => {
      const user = userEvent.setup()
      render(<App />)

      await user.type(screen.getByLabelText(/loan amount/i), '10000000.01')
      await user.type(screen.getByLabelText(/loan term/i), '36')
      await user.selectOptions(screen.getByLabelText(/risk band/i), 'LOW')
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      expect(await screen.findByText(AMOUNT_ERR)).toBeInTheDocument()
      expect(fetch).not.toHaveBeenCalled()
    })

    it('rejects loan tenure exceeding the maximum boundary (361 months)', async () => {
      const user = userEvent.setup()
      render(<App />)

      await user.type(screen.getByLabelText(/loan amount/i), '50000')
      await user.type(screen.getByLabelText(/loan term/i), '361')
      await user.selectOptions(screen.getByLabelText(/risk band/i), 'HIGH')
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      expect(await screen.findByText(TERM_ERR)).toBeInTheDocument()
      expect(fetch).not.toHaveBeenCalled()
    })
  })

  describe('Degraded State & Error Management', () => {
    it('displays error banner and debounces Retry Quote button on HTTP 502 Bad Gateway', async () => {
      const user = userEvent.setup()
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
        JSON.stringify({ detail: 'Vendor verification unavailable. Please retry.' }),
        { status: 502, headers: { 'Content-Type': 'application/json' } })))

      render(<App />)
      await fillValid(user)
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      expect(await screen.findByText(/vendor verification unavailable/i)).toBeInTheDocument()
      const retryBtn = screen.getByRole('button', { name: /retry quote/i })
      expect(retryBtn).toBeInTheDocument()
      // Button is initially debounced (disabled) for 2 seconds to mitigate rapid hammering
      expect(retryBtn).toBeDisabled()
    })

    it('displays prominent circuit-breaker guidance banner when backend returns HTTP 503 CIRCUIT_OPEN', async () => {
      const user = userEvent.setup()
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
        JSON.stringify({
          detail: 'The third-party quote service is currently experiencing high error rates. Retries are temporarily paused. Please try again shortly.',
          state: 'CIRCUIT_OPEN'
        }),
        { status: 503, headers: { 'Content-Type': 'application/json' } })))

      render(<App />)
      await fillValid(user)
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      expect(await screen.findByText(/retries are temporarily paused/i)).toBeInTheDocument()
    })

    it('re-arms retry debounce on consecutive errors with identical failure messages', async () => {
      vi.useFakeTimers()

      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
        JSON.stringify({ detail: 'Vendor verification unavailable. Please retry.' }),
        { status: 502, headers: { 'Content-Type': 'application/json' } })))

      render(<App />)
      fireEvent.change(screen.getByLabelText(/loan amount/i), { target: { value: '250000' } })
      fireEvent.change(screen.getByLabelText(/loan term/i), { target: { value: '36' } })
      fireEvent.change(screen.getByLabelText(/risk band/i), { target: { value: 'MEDIUM' } })

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /calculate quote/i }))
      })

      const retryBtn = screen.getByRole('button', { name: /retry quote/i })
      expect(retryBtn).toBeDisabled()

      // Advance past the 2000ms debounce
      act(() => {
        vi.advanceTimersByTime(2100)
      })
      expect(retryBtn).not.toBeDisabled()

      // Second failure with the same error message should re-arm the debounce
      await act(async () => {
        fireEvent.click(retryBtn)
      })
      expect(screen.getByRole('button', { name: /retry quote/i })).toBeDisabled()

      vi.useRealTimers()
    })
  })
})
