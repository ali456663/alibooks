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
  void leavesNormalTextAsQuotedCsv() {
    assertThat(CsvEscaper.escape("Ali Wafa")).isEqualTo("\"Ali Wafa\"");
  }
}
