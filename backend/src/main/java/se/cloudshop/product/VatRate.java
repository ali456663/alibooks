package se.cloudshop.product;

import java.util.Set;
import java.util.OptionalInt;

public final class VatRate {
  private static final Set<Integer> SUPPORTED = Set.of(6, 12, 25);

  private VatRate() {
  }

  public static boolean isSupported(int percent) {
    return SUPPORTED.contains(percent);
  }

  public static String salesAccount(int percent) {
    return switch (percent) {
      case 6 -> "3043";
      case 12 -> "3042";
      case 25 -> "3041";
      default -> throw new IllegalArgumentException("Unsupported VAT rate.");
    };
  }

  public static String outputVatAccount(int percent) {
    return switch (percent) {
      case 6 -> "2631";
      case 12 -> "2621";
      case 25 -> "2611";
      default -> throw new IllegalArgumentException("Unsupported VAT rate.");
    };
  }

  public static OptionalInt salesRate(String accountNumber) {
    for (int rate : SUPPORTED) {
      if (salesAccount(rate).equals(accountNumber)) {
        return OptionalInt.of(rate);
      }
    }
    return OptionalInt.empty();
  }

  public static boolean isOutputVatAccount(String accountNumber) {
    return "2611".equals(accountNumber) || "2621".equals(accountNumber) || "2631".equals(accountNumber);
  }
}
