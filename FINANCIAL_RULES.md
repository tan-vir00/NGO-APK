# Financial Rules

- Monetary amounts are integer poisha. UI input in Taka is multiplied by 100 and rounded to a whole poisha.
- Rates are percentage values such as `12` for 12%.
- `ANNUAL`: interest = principal × rate / 100 × months / 12, rounded half-up to the nearest poisha.
- `FULL_TERM`: interest = principal × rate / 100, rounded half-up to the nearest poisha.
- The installment schedule divides total payable evenly and assigns all remainder to the final installment.
- Default payment allocation is oldest installment first. Amount remaining after all scheduled installments are fully allocated is an advance balance (derived as valid payments minus allocated installment amounts).
- Payment allocation is always rebuilt from the loan's VALID (non-cancelled) payments in chronological order (payment date, then id), oldest installment first. Recording or cancelling a payment re-runs this inside one Room transaction, so allocations and installment totals never drift.
- Cancelling a payment keeps the original payment row (`cancelled = 1`), keeps its old allocation rows as history, and writes a `PAYMENT_CANCELLED` audit entry that includes the mandatory reason. A payment can be cancelled only once.
- Outstanding balance is per loan `max(0, totalPayable - validPaid)` where `totalPayable = principal + interest`. Advance is `max(0, validPaid - totalPayable)`. Portfolio totals sum these per loan (an advance on one loan never offsets another loan's balance) and exclude cancelled loans and cancelled payments. Identity: `outstanding = totalPayable - collected + advance`.
- This build is a development baseline; auditability and cancellation behavior require full end-to-end verification before production use.
