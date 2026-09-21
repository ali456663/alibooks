package se.cloudshop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

@JsonTest
@Import(JsonNumberConfig.class)
class JsonNumberConfigTest {

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void rejectsDecimalAmountsInsteadOfTruncatingThemToWholeKrona() {
    assertThatThrownBy(() -> objectMapper.readValue("{\"amount\":10.50}", IntegerAmount.class))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  void acceptsWholeKronaAmounts() throws JsonProcessingException {
    assertThat(objectMapper.readValue("{\"amount\":125}", IntegerAmount.class).amount()).isEqualTo(125);
  }

  public record IntegerAmount(int amount) {
  }
}
