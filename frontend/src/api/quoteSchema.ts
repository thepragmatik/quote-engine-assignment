import { z } from 'zod'

/**
 * Zod validation schema for client-side loan quote requests.
 *
 * @remarks
 * ATTENTION(security): This schema strictly mirrors the server-side Jakarta Bean Validation
 * annotations on {@code QuoteRequest} (matching bounds, numerical scales, enum sets, and exact
 * error strings).
 *
 * The client environment is fundamentally untrusted — the backend re-validates every field
 * independently. This schema exists to provide immediate, accessible user feedback and prevent
 * unnecessary network dispatches for invalid payloads.
 */
export const quoteSchema = z.object({
  /**
   * Principal loan amount requested in currency units.
   * Bound: [$1,000.00, $10,000,000.00].
   */
  loanAmount: z
    .number({ message: 'Loan amount must be between $1,000 and $10,000,000.' })
    .min(1000, 'Loan amount must be between $1,000 and $10,000,000.')
    .max(10000000, 'Loan amount must be between $1,000 and $10,000,000.'),

  /**
   * Loan tenure duration in months.
   * Bound: [6, 360] integer months.
   */
  loanTermInMonths: z
    .number({ message: 'Loan term must be between 6 and 360 months.' })
    .int('Loan term must be between 6 and 360 months.')
    .min(6, 'Loan term must be between 6 and 360 months.')
    .max(360, 'Loan term must be between 6 and 360 months.'),

  /**
   * Operational risk assessment tier.
   * Permitted values: 'LOW' | 'MEDIUM' | 'HIGH'.
   */
  riskBand: z.enum(['LOW', 'MEDIUM', 'HIGH'], {
    message: 'Please select a valid risk band.',
  }),
})

/**
 * TypeScript type inferred directly from {@link quoteSchema}.
 */
export type QuotePayload = z.infer<typeof quoteSchema>
