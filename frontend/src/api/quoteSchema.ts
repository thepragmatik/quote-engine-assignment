import { z } from 'zod'

// ATTENTION(security): this schema mirrors the backend's bean validation EXACTLY
// (bounds + enum + error strings). The client is untrusted — the backend
// re-validates; this is UX, not the security boundary.
export const quoteSchema = z.object({
  loanAmount: z
    .number({ message: 'Loan amount must be between $1,000 and $10,000,000.' })
    .min(1000, 'Loan amount must be between $1,000 and $10,000,000.')
    .max(10000000, 'Loan amount must be between $1,000 and $10,000,000.'),
  loanTermInMonths: z
    .number({ message: 'Loan term must be between 6 and 360 months.' })
    .int('Loan term must be between 6 and 360 months.')
    .min(6, 'Loan term must be between 6 and 360 months.')
    .max(360, 'Loan term must be between 6 and 360 months.'),
  riskBand: z.enum(['LOW', 'MEDIUM', 'HIGH'], {
    message: 'Please select a valid risk band.',
  }),
})

export type QuotePayload = z.infer<typeof quoteSchema>
