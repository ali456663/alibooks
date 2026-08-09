package se.cloudshop.export;

public final class CsvEscaper {

  private CsvEscaper() {
  }

  public static String escape(String value) {
    if (value == null) {
      return "";
    }

    String safeValue = neutralizeFormula(value);
    return "\"" + safeValue.replace("\"", "\"\"") + "\"";
  }

  private static String neutralizeFormula(String value) {
    if (value.isBlank()) {
      return value;
    }

    char first = value.charAt(0);
    if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
      return "'" + value;
    }

    return value;
  }
}
