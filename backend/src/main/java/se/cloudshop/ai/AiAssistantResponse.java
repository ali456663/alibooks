package se.cloudshop.ai;

public record AiAssistantResponse(
    String answer,
    String targetView,
    String provider
) {
}
