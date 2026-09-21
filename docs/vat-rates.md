# VAT rate handling

AliBooks currently supports Swedish domestic output VAT rates of 25%, 12% and 6% for service invoices and manually booked Stripe website sales.

## Posting rules

| VAT rate | Revenue account | Output VAT account |
| --- | --- | --- |
| 25% | 3041 | 2611 |
| 12% | 3042 | 2621 |
| 6% | 3043 | 2631 |

The service rate is copied to each invoice when the invoice is created. Editing a service later does not change existing invoices. Credit invoices retain the original invoice rate. For manually booked Stripe sales, the operator selects the rate and enters the gross amount; AliBooks calculates the net amount and VAT using whole-krona rounding.

The application does not determine the legal VAT classification of an offer. The business must verify the rate for the exact product or service, customer, and transaction before issuing or posting it. In particular, a fitness-related label alone does not establish that the reduced rate applies. If the transaction is cross-border, exempt, mixed-rate, or otherwise outside these three domestic rates, do not use this simplified workflow.

The VAT control compares each supported sales account against its matching output-VAT account. Other 3xxx sales accounts are reported as unclassified and block VAT settlement/period close until reviewed; they are never silently assumed to be 25%.

## Verification

Reconcile bases and output VAT by rate in the VAT report against invoices, credit invoices, and source evidence before filing. VAT settlement clears accounts 2611, 2621, 2631, and 2641 into the VAT settlement account. Existing historical postings are not rewritten by the schema update.

Use current Skatteverket guidance and professional advice to classify the specific offer:

- [Skatteverket: VAT rates and exemptions](https://www.skatteverket.se/foretag/moms/saljavarorochtjanster/momssatspavarorochtjanster.4.58d555751259e4d66168000409.html)
- [Skatteverket legal guidance: sport and physical training](https://www4.skatteverket.se/rattsligvagledning/edition/2026.3/395026.html)
