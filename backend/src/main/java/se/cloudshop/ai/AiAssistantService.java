package se.cloudshop.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiAssistantService {

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String hfToken;
  private final String model;
  private final String baseUrl;

  public AiAssistantService(
      ObjectMapper objectMapper,
      @Value("${ai.huggingface.token:}") String hfToken,
      @Value("${ai.huggingface.model:moonshotai/Kimi-K2-Instruct-0905}") String model,
      @Value("${ai.huggingface.base-url:https://router.huggingface.co/v1}") String baseUrl
  ) {
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();
    this.hfToken = hfToken;
    this.model = model;
    this.baseUrl = baseUrl;
  }

  public AiAssistantResponse answer(AiAssistantRequest request) {
    String question = clean(request == null ? "" : request.question());
    String language = "en".equalsIgnoreCase(clean(request == null ? "" : request.language())) ? "en" : "sv";
    String targetView = targetView(question);

    if (question.isBlank()) {
      return new AiAssistantResponse(emptyQuestionAnswer(language), "", "local");
    }

    if (hfToken == null || hfToken.isBlank()) {
      return new AiAssistantResponse(localAnswer(question, language), targetView, "local");
    }

    try {
      String aiAnswer = requestHuggingFace(question, language, clean(request == null ? "" : request.context()));
      if (aiAnswer == null || aiAnswer.isBlank()) {
        return new AiAssistantResponse(localAnswer(question, language), targetView, "local");
      }
      return new AiAssistantResponse(aiAnswer, targetView, "huggingface");
    } catch (Exception exception) {
      return new AiAssistantResponse(localAnswer(question, language), targetView, "local");
    }
  }

  private String requestHuggingFace(String question, String language, String context) throws Exception {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("model", model);

    ArrayNode messages = body.putArray("messages");
    messages.addObject()
        .put("role", "system")
        .put("content", systemPrompt(language));
    messages.addObject()
        .put("role", "user")
        .put("content", "Question: " + question + "\n\nAliBooks context: " + context);

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/chat/completions"))
        .timeout(Duration.ofSeconds(25))
        .header("Authorization", "Bearer " + hfToken)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
        .build();

    HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      return "";
    }

    JsonNode root = objectMapper.readTree(response.body());
    return root.path("choices").path(0).path("message").path("content").asText("");
  }

  private String systemPrompt(String language) {
    String responseLanguage = "sv".equals(language) ? "Swedish" : "English";
    return """
        You are AliBooks assistant, a practical helper inside a Swedish invoicing and bookkeeping app.
        Answer in %s.
        Help with invoices, customers, receipts, expenses, VAT, payments, Stripe, reports, settings and exports.
        Keep answers short, concrete and step-by-step.
        Mention that exact tax and legal dates should be verified with Skatteverket when relevant.
        Do not claim to replace a certified accountant or legal advisor.
        Never ask the user to reveal API keys, passwords, personnummer lists or other secrets.
        """.formatted(responseLanguage);
  }

  private String localAnswer(String question, String language) {
    String normalized = normalizedQuestion(question);

    if (normalized.contains("faktura") || normalized.contains("invoice")) {
      return "sv".equals(language)
          ? "AliBooks-assistenten: Skapa eller valj kund, valj tjanst och skapa faktura. Nar fakturan skapas bokfors den normalt automatiskt: 1510 debet, 3041 kredit och 2611 kredit. Nar kunden betalar bokas 1930 debet och 1510 kredit."
          : "AliBooks assistant: Create or choose a customer, choose a service and create the invoice. When it is created it is normally booked automatically: 1510 debit, 3041 credit and 2611 credit. When the customer pays, book 1930 debit and 1510 credit.";
    }

    if (normalized.contains("moms") || normalized.contains("vat")) {
      return "sv".equals(language)
          ? "AliBooks-assistenten: Ga till Momsrapport. Appen summerar utgaende moms minus ingaende moms. Kontrollera alltid period och exakta datum hos Skatteverket innan du deklarerar."
          : "AliBooks assistant: Go to VAT report. The app summarizes output VAT minus input VAT. Always verify the period and exact dates with the tax authority before filing.";
    }

    if (normalized.contains("kvitto") || normalized.contains("underlag") || normalized.contains("receipt")) {
      return "sv".equals(language)
          ? "AliBooks-assistenten: Ga till Underlag eller Kostnader. Ladda upp kvitto eller faktura, fyll datum, beskrivning, totalbelopp, moms och kategori. Spara underlag tryggt for arkivering."
          : "AliBooks assistant: Go to Uploaded or Expenses. Upload the receipt or invoice, enter date, description, total amount, VAT and category. Keep documents safely for archiving.";
    }

    if (normalized.contains("rapport") || normalized.contains("export") || normalized.contains("report")) {
      return "sv".equals(language)
          ? "AliBooks-assistenten: Ga till Rapporter for resultat- och balansrapport. I Bokforing kan du exportera verifikat och huvudbok som CSV."
          : "AliBooks assistant: Go to Reports for profit and loss and balance report. In Bookkeeping you can export vouchers and account ledger as CSV.";
    }

    if (normalized.contains("bank") || normalized.contains("betal") || normalized.contains("payment") || normalized.contains("stripe")) {
      return "sv".equals(language)
          ? "AliBooks-assistenten: Ga till Betalningar. Du kan registrera betalning, delbetalning, Stripe-forsaljning, Stripe-utbetalning eller anvanda CSV-bankimport."
          : "AliBooks assistant: Go to Payments. You can register payment, partial payment, Stripe sale, Stripe payout or use CSV bank import.";
    }

    return "sv".equals(language)
        ? "AliBooks-assistenten: Jag kan hjalpa med fakturor, bokforing, moms, rapporter, underlag, bankimport, betalningar och installningar. Skriv garna en mer konkret fraga."
        : "AliBooks assistant: I can help with invoices, bookkeeping, VAT, reports, receipts, bank import, payments and settings. Please ask a more specific question.";
  }

  private String targetView(String question) {
    String normalized = normalizedQuestion(question);
    if (normalized.contains("faktura") || normalized.contains("invoice")) return "invoices";
    if (normalized.contains("bokfor") || normalized.contains("verifikat") || normalized.contains("journal")) return "bookkeeping";
    if (normalized.contains("kvitto") || normalized.contains("underlag") || normalized.contains("receipt")) return "uploaded";
    if (normalized.contains("rapport") || normalized.contains("export") || normalized.contains("report")) return "reports";
    if (normalized.contains("moms") || normalized.contains("vat")) return "vat";
    if (normalized.contains("bank") || normalized.contains("betal") || normalized.contains("payment") || normalized.contains("stripe")) return "payments";
    if (normalized.contains("install") || normalized.contains("setting") || normalized.contains("smtp")) return "settings";
    return "";
  }

  private String emptyQuestionAnswer(String language) {
    return "sv".equals(language)
        ? "AliBooks-assistenten: Skriv en fraga, till exempel hur du bokfor en faktura eller laddar upp underlag."
        : "AliBooks assistant: Write a question, for example how to bookkeep an invoice or upload receipts.";
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private String normalizedQuestion(String value) {
    return Normalizer.normalize(clean(value).toLowerCase(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "");
  }
}
