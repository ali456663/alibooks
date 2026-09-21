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
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (Character.isWhitespace(current) || Character.isISOControl(current) || current == '\uFEFF') {
        continue;
      }

      if (current == '=' || current == '+' || current == '-' || current == '@') {
        return "'" + value;
      }
      break;
    }

    return value;
  }
}
