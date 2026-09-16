package se.cloudshop.bank;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import se.cloudshop.auth.AuthHeader;

@RestController
public class BankJournalMatchController {
  private final AuthHeader auth;
  private final BankJournalMatchService matches;

  public BankJournalMatchController(AuthHeader auth, BankJournalMatchService matches) {
    this.auth = auth;
    this.matches = matches;
  }

  public record MatchRequest(Long journalEntryId) {}

  @GetMapping("/bank-reconciliations/{id}/journal-candidates")
  public List<BankJournalMatchService.Candidate> candidates(
      @RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable long id) {
    auth.requireValidToken(authorization);
    return matches.candidates(id);
  }

  @PostMapping("/bank-reconciliations/{id}/journal-link")
  public BankReconciliationEntry match(@RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable long id, @RequestBody MatchRequest request) {
    auth.requireValidToken(authorization);
    return matches.match(id, request == null ? null : request.journalEntryId(), authorization);
  }
}
