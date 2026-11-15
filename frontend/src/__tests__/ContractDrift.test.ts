import { describe, it, expect } from 'vitest'
import { quoteSchema, type OpenApiQuoteRequest } from '../api/quoteSchema'

describe('OpenAPI Contract Drift & Schema Synchronization', () => {
  it('accepts valid payloads conforming to generated OpenApiQuoteRequest', () => {
    const validSample: OpenApiQuoteRequest = {
      loanAmount: 250000,
      loanTermInMonths: 36,
      riskBand: 'MEDIUM',
    }

    const result = quoteSchema.safeParse(validSample)
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data).toEqual(validSample)
    }
  })

  it('rejects values violating OpenAPI boundary constraints', () => {
    const outOfBoundsMin: OpenApiQuoteRequest = {
      loanAmount: 999.99,
      loanTermInMonths: 36,
      riskBand: 'LOW',
    }
    expect(quoteSchema.safeParse(outOfBoundsMin).success).toBe(false)

    const outOfBoundsMax: OpenApiQuoteRequest = {
      loanAmount: 10000000.01,
      loanTermInMonths: 36,
      riskBand: 'HIGH',
    }
    expect(quoteSchema.safeParse(outOfBoundsMax).success).toBe(false)

    const outOfBoundsTerm: OpenApiQuoteRequest = {
      loanAmount: 50000,
      loanTermInMonths: 5,
      riskBand: 'MEDIUM',
    }
    expect(quoteSchema.safeParse(outOfBoundsTerm).success).toBe(false)
  })
})
