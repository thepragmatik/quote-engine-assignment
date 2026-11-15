import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../App'

// ATTENTION(test): these tests assert the FR-1 contract strings LITERALLY —
// they are a spec requirement, not cosmetic copy.
const AMOUNT_ERR = 'Loan amount must be between $1,000 and $10,000,000.'
const TERM_ERR = 'Loan term must be between 6 and 360 months.'
const BAND_ERR = 'Please select a valid risk band.'

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/loan amount/i), '250000')
  await user.type(screen.getByLabelText(/loan term/i), '36')
  await user.selectOptions(screen.getByLabelText(/risk band/i), 'MEDIUM')
}

describe('QuoteForm', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn())
  })

  it('shows the three exact FR-1 error strings on invalid input without calling fetch', async () => {
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

  it('disables the button and shows Calculating… while the request is in flight', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))) // never resolves
    render(<App />)
    await fillValid(user)
    await user.click(screen.getByRole('button', { name: /calculate quote/i }))
    const btn = await screen.findByRole('button', { name: /calculating/i })
    expect(btn).toBeDisabled()
  })

  it('shows an error banner with the reason and a Retry Quote button on 502', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ detail: 'Vendor verification unavailable. Please retry.' }),
      { status: 502, headers: { 'Content-Type': 'application/json' } })))
    render(<App />)
    await fillValid(user)
    await user.click(screen.getByRole('button', { name: /calculate quote/i }))
    expect(await screen.findByText(/vendor verification unavailable/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /retry quote/i })).toBeInTheDocument()
  })

  it('shows the circuit-breaker banner on 503 CIRCUIT_OPEN', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ detail: 'The third-party quote service is currently experiencing high error rates. Retries are temporarily paused. Please try again shortly.', state: 'CIRCUIT_OPEN' }),
      { status: 503, headers: { 'Content-Type': 'application/json' } })))
    render(<App />)
    await fillValid(user)
    await user.click(screen.getByRole('button', { name: /calculate quote/i }))
    expect(await screen.findByText(/retries are temporarily paused/i)).toBeInTheDocument()
  })

  it('renders the result card with percent and currency formatting on 200', async () => {
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
})
