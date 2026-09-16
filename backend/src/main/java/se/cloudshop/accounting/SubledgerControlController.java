package se.cloudshop.accounting;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;

@RestController
public class SubledgerControlController {
  private final AuthHeader auth;
  private final SubledgerControlService service;

  public SubledgerControlController(AuthHeader auth, SubledgerControlService service) {
    this.auth = auth;
    this.service = service;
  }

  @GetMapping("/subledger-control")
  public SubledgerControlReport report(@RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    auth.requireValidToken(authorization);
    return service.createReport(asOf);
  }
}
