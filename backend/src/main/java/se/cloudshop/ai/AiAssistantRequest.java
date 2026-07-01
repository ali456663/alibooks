package se.cloudshop.ai;

public record AiAssistantRequest(
    String question,
    String language,
    String context
) {
}
