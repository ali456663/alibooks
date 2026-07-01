package se.cloudshop.ai;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;

@RestController
public class AiAssistantController {

  private final AuthHeader authHeader;
  private final AiAssistantService aiAssistantService;

  public AiAssistantController(AuthHeader authHeader, AiAssistantService aiAssistantService) {
    this.authHeader = authHeader;
    this.aiAssistantService = aiAssistantService;
  }

  @PostMapping("/ai/assistant")
  public AiAssistantResponse askAssistant(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody AiAssistantRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return aiAssistantService.answer(request);
  }
}
