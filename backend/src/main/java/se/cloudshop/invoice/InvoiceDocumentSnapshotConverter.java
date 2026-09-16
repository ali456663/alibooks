package se.cloudshop.invoice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class InvoiceDocumentSnapshotConverter implements AttributeConverter<InvoiceDocumentSnapshot, String> {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(InvoiceDocumentSnapshot snapshot) {
    if (snapshot == null) return null;
    try {
      return JSON.writeValueAsString(snapshot);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invoice document snapshot could not be stored.");
    }
  }

  @Override
  public InvoiceDocumentSnapshot convertToEntityAttribute(String json) {
    if (json == null) return null;
    try {
      InvoiceDocumentSnapshot snapshot = JSON.readValue(json, InvoiceDocumentSnapshot.class);
      if (snapshot == null || snapshot.version() != 1) throw new IllegalStateException("Unsupported invoice document snapshot version.");
      return snapshot;
    } catch (JsonProcessingException exception) {
      // Do not echo stored customer data or silently substitute current register values.
      throw new IllegalStateException("Invoice document snapshot is invalid.");
    }
  }
}
