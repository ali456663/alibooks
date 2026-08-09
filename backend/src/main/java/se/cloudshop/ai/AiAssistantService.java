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
  private final String geminiApiKey;
  private final String geminiModel;
  private final String geminiBaseUrl;
  private final String hfToken;
  private final String hfModel;
  private final String hfBaseUrl;
  private final String openAiCompatibleApiKey;
  private final String openAiCompatibleModel;
  private final String openAiCompatibleBaseUrl;
  private final String openAiCompatibleProviderName;

  public AiAssistantService(
      ObjectMapper objectMapper,
      @Value("${ai.gemini.api-key:}") String geminiApiKey,
      @Value("${ai.gemini.model:gemini-3.5-flash}") String geminiModel,
      @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String geminiBaseUrl,
      @Value("${ai.huggingface.token:}") String hfToken,
      @Value("${ai.huggingface.model:moonshotai/Kimi-K2-Instruct-0905}") String hfModel,
      @Value("${ai.huggingface.base-url:https://router.huggingface.co/v1}") String hfBaseUrl,
      @Value("${ai.openai-compatible.api-key:}") String openAiCompatibleApiKey,
      @Value("${ai.openai-compatible.model:}") String openAiCompatibleModel,
      @Value("${ai.openai-compatible.base-url:}") String openAiCompatibleBaseUrl,
      @Value("${ai.openai-compatible.provider-name:openai-compatible}") String openAiCompatibleProviderName
  ) {
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();
    this.geminiApiKey = geminiApiKey;
    this.geminiModel = geminiModel;
    this.geminiBaseUrl = geminiBaseUrl;
    this.hfToken = hfToken;
    this.hfModel = hfModel;
    this.hfBaseUrl = hfBaseUrl;
    this.openAiCompatibleApiKey = openAiCompatibleApiKey;
    this.openAiCompatibleModel = openAiCompatibleModel;
    this.openAiCompatibleBaseUrl = openAiCompatibleBaseUrl;
    this.openAiCompatibleProviderName = openAiCompatibleProviderName;
  }

  public AiAssistantResponse answer(AiAssistantRequest request) {
    String question = clean(request == null ? "" : request.question());
    String language = "en".equalsIgnoreCase(clean(request == null ? "" : request.language())) ? "en" : "sv";
    String targetView = targetView(question);

    if (question.isBlank()) {
      return new AiAssistantResponse(emptyQuestionAnswer(language), "", "local");
    }

    String safeQuestion = sanitizeForExternalAi(question);
    String context = sanitizeForExternalAi(clean(request == null ? "" : request.context()));

    if (hasText(openAiCompatibleApiKey) && hasText(openAiCompatibleBaseUrl) && hasText(openAiCompatibleModel)) {
      try {
        String aiAnswer = requestOpenAiCompatible(safeQuestion, language, context);
        if (aiAnswer != null && !aiAnswer.isBlank()) {
          return new AiAssistantResponse(aiAnswer, targetView, providerName());
        }
      } catch (Exception exception) {
        // Try the next configured provider before falling back to local rules.
      }
    }

    if (hasText(geminiApiKey)) {
      try {
        String aiAnswer = requestGemini(safeQuestion, language, context);
        if (aiAnswer != null && !aiAnswer.isBlank()) {
          return new AiAssistantResponse(aiAnswer, targetView, "gemini");
        }
      } catch (Exception exception) {
        // Try the next configured provider before falling back to local rules.
      }
    }

    if (hasText(hfToken)) {
      try {
        String aiAnswer = requestHuggingFace(safeQuestion, language, context);
        if (aiAnswer != null && !aiAnswer.isBlank()) {
          return new AiAssistantResponse(aiAnswer, targetView, "huggingface");
        }
      } catch (Exception exception) {
        // Local rules keep the assistant useful when external AI is unavailable.
      }
    }

    return new AiAssistantResponse(localAnswer(question, language), targetView, "local");
  }

  private String requestOpenAiCompatible(String question, String language, String context) throws Exception {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("model", openAiCompatibleModel);

    ArrayNode messages = body.putArray("messages");
    messages.addObject()
        .put("role", "system")
        .put("content", systemPrompt(language));
    messages.addObject()
        .put("role", "user")
        .put("content", "Question: " + question + "\n\nAliBooks safe context: " + context);

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(normalizedBaseUrl(openAiCompatibleBaseUrl) + "/chat/completions"))
        .timeout(Duration.ofSeconds(25))
        .header("Authorization", "Bearer " + openAiCompatibleApiKey)
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

  private String requestGemini(String question, String language, String context) throws Exception {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("model", geminiModel);
    body.put("system_instruction", systemPrompt(language));
    body.put("input", "Question: " + question + "\n\nAliBooks context: " + context);

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(geminiBaseUrl + "/interactions"))
        .timeout(Duration.ofSeconds(25))
        .header("x-goog-api-key", geminiApiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
        .build();

    HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      return "";
    }

    JsonNode root = objectMapper.readTree(response.body());
    return root.path("output_text").asText("");
  }

  private String requestHuggingFace(String question, String language, String context) throws Exception {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("model", hfModel);

    ArrayNode messages = body.putArray("messages");
    messages.addObject()
        .put("role", "system")
        .put("content", systemPrompt(language));
    messages.addObject()
        .put("role", "user")
        .put("content", "Question: " + question + "\n\nAliBooks context: " + context);

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(hfBaseUrl + "/chat/completions"))
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
        The provided AliBooks context is intentionally anonymized and minimized.
        Never ask the user to reveal API keys, passwords, personnummer lists or other secrets.
        If the user asks about a specific person, answer using the visible app workflow instead of requesting private data.
        """.formatted(responseLanguage);
  }

  private String localAnswer(String question, String language) {
    String normalized = normalizedQuestion(question);

    if (isTraceabilityQuestion(normalized)) {
      return "sv".equals(language)
          ? "AliBooks-assistenten: Ga till Resultat- och balansdiagnos och oppna Verifikationskedja. Dar kan du klicka pa ett rapportkonto for att se huvudbok, verifikat, faktura, betalning eller underlag bakom beloppet. Exportera verifikationskedja.csv nar du vill kontrollera sparbarheten."
          : "AliBooks assistant: Go to Financial diagnostics and open the audit trail. There you can click a report account to see the ledger, vouchers, invoice, payment or evidence behind the amount. Export verifikationskedja.csv when you want to review traceability.";
    }

    if (isFinancialDiagnosticsQuestion(normalized)) {
      return "sv".equals(language)
          ? "AliBooks-assistenten: Ga till Resultat- och balansdiagnos. Dar ser du resultat, balans, risker, konton som paverkar siffrorna och om nagot bor kontrolleras innan bokslut."
          : "AliBooks assistant: Go to Financial diagnostics. There you can review profit, balance, risks, accounts that affect the numbers and anything that should be checked before closing.";
    }

    if (isServiceJobQuestion(normalized)) {
      return "sv".equals(language)
          ? "AliBooks-assistenten: Ga till Servicejobb. Dar kan du planera jobb, bekrafta bokningar, skriva ut arbetsorder och dagsschema, se schemakrockar, rakna preliminart RUT/ROT pa arbetsdelen, skapa faktura, exportera redo ansokningar och oppna utskrivbart RUT/ROT-underlag. Kontrollera alltid aktuella villkor hos Skatteverket innan riktig RUT/ROT-ansokan."
          : "AliBooks assistant: Go to Service jobs. There you can plan jobs, confirm bookings, print work orders and daily schedules, see schedule conflicts, calculate preliminary RUT/ROT on the labor part, create an invoice, export ready claims and open printable RUT/ROT evidence. Always verify current rules with Skatteverket before a real RUT/ROT claim.";
    }

    if (normalized.contains("faktura") || normalized.contains("invoice")) {
      return "sv".equals(language)
          ? "AliBooks-assistenten: Skapa eller valj kund, valj tjanst och skapa faktura. Vid faktureringsmetoden bokas fakturan normalt som 1510 debet, 3041 kredit och 2611 kredit nar den skapas, och betalningen som 1930 debet och 1510 kredit. Vid kontantmetoden bokas forsaljning och moms forst nar betalningen registreras: 1930 debet, 3041 kredit och 2611 kredit."
          : "AliBooks assistant: Create or choose a customer, choose a service and create the invoice. With invoice method, the invoice is normally booked as 1510 debit, 3041 credit and 2611 credit when created, and payment as 1930 debit and 1510 credit. With cash method, sales and VAT are booked when payment is registered: 1930 debit, 3041 credit and 2611 credit.";
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
    if (isTraceabilityQuestion(normalized) || isFinancialDiagnosticsQuestion(normalized)) return "financialDiagnostics";
    if (isServiceJobQuestion(normalized)) return "serviceJobs";
    if (normalized.contains("faktura") || normalized.contains("invoice")) return "invoices";
    if (normalized.contains("bokfor") || normalized.contains("verifikat") || normalized.contains("journal")) return "bookkeeping";
    if (normalized.contains("kvitto") || normalized.contains("underlag") || normalized.contains("receipt")) return "uploaded";
    if (normalized.contains("rapport") || normalized.contains("export") || normalized.contains("report")) return "reports";
    if (normalized.contains("moms") || normalized.contains("vat")) return "vat";
    if (normalized.contains("bank") || normalized.contains("betal") || normalized.contains("payment") || normalized.contains("stripe")) return "payments";
    if (normalized.contains("install") || normalized.contains("setting") || normalized.contains("smtp")) return "settings";
    return "";
  }

  private boolean isTraceabilityQuestion(String normalized) {
    return normalized.contains("verifikationskedja")
        || normalized.contains("sparbarhet")
        || normalized.contains("varifran kommer")
        || normalized.contains("vilka verifikat")
        || normalized.contains("rapportbelopp")
        || normalized.contains("audit trail")
        || normalized.contains("drilldown")
        || normalized.contains("where does this amount")
        || normalized.contains("which vouchers");
  }

  private boolean isFinancialDiagnosticsQuestion(String normalized) {
    return normalized.contains("resultatdiagnos")
        || normalized.contains("balansdiagnos")
        || normalized.contains("financial diagnostics")
        || normalized.contains("profit diagnosis")
        || normalized.contains("balance diagnosis")
        || normalized.contains("bokslutskontroll");
  }

  private boolean isServiceJobQuestion(String normalized) {
    return normalized.contains("servicejobb")
        || normalized.contains("schema")
        || normalized.contains("rut")
        || normalized.contains("rot")
        || normalized.contains("service job")
        || normalized.contains("schedule");
  }

  private String emptyQuestionAnswer(String language) {
    return "sv".equals(language)
        ? "AliBooks-assistenten: Skriv en fraga, till exempel hur du bokfor en faktura eller laddar upp underlag."
        : "AliBooks assistant: Write a question, for example how to bookkeep an invoice or upload receipts.";
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String normalizedBaseUrl(String value) {
    String cleaned = clean(value);
    while (cleaned.endsWith("/")) {
      cleaned = cleaned.substring(0, cleaned.length() - 1);
    }
    return cleaned;
  }

  private String providerName() {
    String cleaned = clean(openAiCompatibleProviderName).toLowerCase();
    return cleaned.isBlank() ? "openai-compatible" : cleaned;
  }

  private String sanitizeForExternalAi(String value) {
    return clean(value)
        .replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "[redacted-email]")
        .replaceAll("\\b\\d{6,8}[-+]?\\d{4}\\b", "[redacted-personnummer]")
        .replaceAll("(?i)\\b(personnummer|pnr)\\s*[:=]?\\s*[^,;\\n]+", "$1: [redacted-personnummer]")
        .replaceAll("(?i)\\b(adress|address)\\s*[:=]?\\s*[^,;\\n]+", "$1: [redacted-address]")
        .replaceAll("(?i)\\b(tel|telefon|phone)\\s*[:=]?\\s*[+\\d][+\\d\\s()-]{6,}", "$1: [redacted-phone]")
        .replaceAll("\\+?\\d[\\d\\s()-]{7,}\\d", "[redacted-phone]");
  }

  private String normalizedQuestion(String value) {
    return Normalizer.normalize(clean(value).toLowerCase(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "");
  }
}
