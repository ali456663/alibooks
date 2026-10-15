package se.cloudshop.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.auth.AuthHeader;

@RestController
public class VoucherAuditReconciliationController {

  private final AuthHeader authHeader;
  private final VoucherAuditReconciliationService reconciliation;

  public VoucherAuditReconciliationController(AuthHeader authHeader,
                                              VoucherAuditReconciliationService reconciliation) {
    this.authHeader = authHeader;
    this.reconciliation = reconciliation;
  }

  @GetMapping("/audit-events/voucher-amounts")
  public VoucherAuditReconciliationService.Report getVoucherAmounts(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
    authHeader.requireValidToken(authorizationHeader);
    return reconciliation.reconcile();
  }
}
