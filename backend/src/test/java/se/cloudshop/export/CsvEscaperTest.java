package se.cloudshop.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvEscaperTest {

  @Test
  void escapesQuotesAndCommas() {
    assertThat(CsvEscaper.escape("Ali, \"Muscle\""))
        .isEqualTo("\"Ali, \"\"Muscle\"\"\"");
  }

  @Test
  void neutralizesSpreadsheetFormulaPrefixes() {
    assertThat(CsvEscaper.escape("=HYPERLINK(\"http://example.com\")"))
        .isEqualTo("\"'=HYPERLINK(\"\"http://example.com\"\")\"");
    assertThat(CsvEscaper.escape("+4670")).isEqualTo("\"'+4670\"");
    assertThat(CsvEscaper.escape("-danger")).isEqualTo("\"'-danger\"");
    assertThat(CsvEscaper.escape("@cmd")).isEqualTo("\"'@cmd\"");
  }

  @Test
  void neutralizesFormulaAfterLeadingWhitespaceAndControlCharacters() {
    assertThat(CsvEscaper.escape("  =1+1")).isEqualTo("\"'  =1+1\"");
    assertThat(CsvEscaper.escape("\t@SUM(A1:A2)")).isEqualTo("\"'\t@SUM(A1:A2)\"");
    assertThat(CsvEscaper.escape("\uFEFF-HYPERLINK(url)")).isEqualTo("\"'\uFEFF-HYPERLINK(url)\"");
    assertThat(CsvEscaper.escape("  ordinary text")).isEqualTo("\"  ordinary text\"");
  }

  @Test
  void leavesNormalTextAsQuotedCsv() {
    assertThat(CsvEscaper.escape("Ali Wafa")).isEqualTo("\"Ali Wafa\"");
  }
}
