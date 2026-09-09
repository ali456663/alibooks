package se.cloudshop.order;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Reject coercion: payment endpoints currently accept whole SEK, not decimals or strings. */
public class WholeKronaAmountDeserializer extends JsonDeserializer<Integer> {
  @Override
  public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
      return (Integer) context.handleUnexpectedToken(Integer.class, parser);
    }
    return parser.getIntValue();
  }
}
