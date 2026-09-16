package se.cloudshop.money;

/** Exact arithmetic for the existing whole-SEK model; this does not add ore support. */
public final class WholeKronaMath {
  private WholeKronaMath() {
  }

  public static int roundedRatio(int amount, int numerator, int denominator) {
    if (numerator < 0 || denominator <= 0) {
      throw new IllegalArgumentException("Ratio requires a nonnegative numerator and positive denominator.");
    }
    // Preserve Math.round's tie rule, but never multiply as int or use float.
    long product = (long) amount * numerator;
    return Math.toIntExact(Math.floorDiv(product + denominator / 2L, denominator));
  }
}
