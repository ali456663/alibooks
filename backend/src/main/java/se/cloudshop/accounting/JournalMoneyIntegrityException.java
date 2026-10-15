package se.cloudshop.accounting;

/** A stored ledger row cannot be trusted; repair requires an explicit reviewed operation. */
public final class JournalMoneyIntegrityException extends RuntimeException {
  public JournalMoneyIntegrityException() {
    super("Bokforingsraden har oforenliga belopp eller valuta. Atgarden har stoppats; granska lagrade data innan du fortsatter.");
  }
}
