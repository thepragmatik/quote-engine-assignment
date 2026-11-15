import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../App'

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/loan amount/i), '250000')
  await user.type(screen.getByLabelText(/loan term/i), '36')
  await user.selectOptions(screen.getByLabelText(/risk band/i), 'MEDIUM')
}

describe('QuoteForm — Adversarial & Security Threat Probing', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn())
  })

  describe('XSS & Content Injection Attacks', () => {
    it('escapes and neutralizes script injection in quoteId without HTML execution', async () => {
      const user = userEvent.setup()
      const xssQuoteId = '<script>alert("PWNED_QUOTE_ID")</script><img src=x onerror=alert(1)>'

      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
        JSON.stringify({
          quoteId: xssQuoteId,
          commissionRate: 0.0231,
          totalCommission: 5775
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )))

      render(<App />)
      await fillValid(user)
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      // Text node must contain the literal string without creating executable script elements
      const quoteIdElem = await screen.findByTestId('quote-id')
      expect(quoteIdElem.textContent).toBe(xssQuoteId)
      expect(document.querySelector('script[src]')).toBeNull()
    })

    it('escapes and neutralizes HTML/event-handler injection in error detail', async () => {
      const user = userEvent.setup()
      const xssError = '<b onmouseover=alert("PWNED_ERROR")>Malicious Error Message</b>'

      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
        JSON.stringify({ detail: xssError }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      )))

      render(<App />)
      await fillValid(user)
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      const alert = await screen.findByRole('alert')
      expect(alert.textContent).toContain(xssError)
      // Assert that no HTML <b> tag with event handler was mounted into the DOM
      expect(alert.querySelector('b[onmouseover]')).toBeNull()
    })
  })

  describe('Corrupted & Hostile Server Response Handling', () => {
    it('handles total network failure (TypeError: Failed to fetch) gracefully without unhandled crashes', async () => {
      const user = userEvent.setup()
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

      render(<App />)
      await fillValid(user)
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      expect(await screen.findByText('Request failed. Please try again.')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /retry quote/i })).toBeInTheDocument()
    })

    it('handles HTTP 200 containing HTML captive-portal/proxy error without UI explosion', async () => {
      const user = userEvent.setup()
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
        '<!DOCTYPE html><html><body>Proxy Authentication Required</body></html>',
        { status: 200, headers: { 'Content-Type': 'text/html' } }
      )))

      render(<App />)
      await fillValid(user)
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      expect(
        await screen.findByText('Invalid response payload received from quotation service.')
      ).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /retry quote/i })).toBeInTheDocument()
    })

    it('rejects HTTP 200 with missing or malformed JSON fields (null rate or non-string id)', async () => {
      const user = userEvent.setup()
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
        JSON.stringify({ unexpectedData: true }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )))

      render(<App />)
      await fillValid(user)
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      expect(
        await screen.findByText('Invalid response payload received from quotation service.')
      ).toBeInTheDocument()
    })

    it('safely wraps and renders exceptionally long upstream error payloads (5,000 characters)', async () => {
      const user = userEvent.setup()
      const hugeError = 'A'.repeat(5000)

      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
        JSON.stringify({ detail: hugeError }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )))

      render(<App />)
      await fillValid(user)
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      const alert = await screen.findByRole('alert')
      expect(alert.textContent).toContain(hugeError)
    })
  })

  describe('Concurreny & Double-Submit Interception', () => {
    it('blocks rapid duplicate submissions while network request is in-flight', async () => {
      const user = userEvent.setup()
      let resolveNetwork: (val: Response) => void

      const pendingPromise = new Promise<Response>((resolve) => {
        resolveNetwork = resolve
      })

      const fetchMock = vi.fn().mockReturnValue(pendingPromise)
      vi.stubGlobal('fetch', fetchMock)

      render(<App />)
      await fillValid(user)

      const submitBtn = screen.getByRole('button', { name: /calculate quote/i })

      // First click dispatches
      await user.click(submitBtn)
      expect(fetchMock).toHaveBeenCalledTimes(1)

      // Subsequent clicks while in-flight are prevented
      await user.click(submitBtn)
      fireEvent.click(submitBtn)
      fireEvent.submit(screen.getByRole('button', { name: /calculating/i }))
      expect(fetchMock).toHaveBeenCalledTimes(1)

      // Resolve the pending network response
      await act(async () => {
        resolveNetwork!(
          new Response(
            JSON.stringify({ quoteId: '123e4567-e89b-42d3-a456-426614174000', commissionRate: 0.0231, totalCommission: 5775 }),
            { status: 200, headers: { 'Content-Type': 'application/json' } }
          )
        )
      })

      expect(await screen.findByText('2.3100%')).toBeInTheDocument()
    })
  })

  describe('Numeric Edge Cases & Type Coercion Resilience', () => {
    it('rejects negative and zero loan amounts prior to network dispatch', async () => {
      const user = userEvent.setup()
      render(<App />)

      await user.type(screen.getByLabelText(/loan amount/i), '-50000')
      await user.type(screen.getByLabelText(/loan term/i), '36')
      await user.selectOptions(screen.getByLabelText(/risk band/i), 'LOW')
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      expect(await screen.findByText('Loan amount must be between $1,000 and $10,000,000.')).toBeInTheDocument()
      expect(fetch).not.toHaveBeenCalled()
    })

    it('rejects fractional loan tenure (e.g. 36.5 months)', async () => {
      const user = userEvent.setup()
      render(<App />)

      await user.type(screen.getByLabelText(/loan amount/i), '250000')
      await user.type(screen.getByLabelText(/loan term/i), '36.5')
      await user.selectOptions(screen.getByLabelText(/risk band/i), 'MEDIUM')
      await user.click(screen.getByRole('button', { name: /calculate quote/i }))

      expect(await screen.findByText('Loan term must be between 6 and 360 months.')).toBeInTheDocument()
      expect(fetch).not.toHaveBeenCalled()
    })
  })
})
