package se.cloudshop.payroll;

import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.auth.AuthHeader;

@RestController
public class PayrollSnapshotController {

  private static final long SNAPSHOT_ID = 1L;

  private final PayrollSnapshotRepository payrollSnapshotRepository;
  private final AuthHeader authHeader;

  public PayrollSnapshotController(PayrollSnapshotRepository payrollSnapshotRepository, AuthHeader authHeader) {
    this.payrollSnapshotRepository = payrollSnapshotRepository;
    this.authHeader = authHeader;
  }

  @GetMapping("/payroll/snapshot")
  public PayrollSnapshot getPayrollSnapshot(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);
    return payrollSnapshotRepository.findById(SNAPSHOT_ID)
        .orElseGet(() -> new PayrollSnapshot(SNAPSHOT_ID, "", null));
  }

  @PutMapping("/payroll/snapshot")
  public PayrollSnapshot savePayrollSnapshot(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody PayrollSnapshotRequest request
  ) {
    authHeader.requireValidToken(authorizationHeader);

    if (request.content() == null || request.content().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payroll snapshot content is required.");
    }

    PayrollSnapshot snapshot = payrollSnapshotRepository.findById(SNAPSHOT_ID)
        .orElseGet(() -> new PayrollSnapshot(SNAPSHOT_ID, "", LocalDateTime.now()));
    snapshot.updateContent(request.content());
    return payrollSnapshotRepository.save(snapshot);
  }
}
