package se.cloudshop.customer;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import se.cloudshop.audit.AuditService;
import se.cloudshop.auth.AuthHeader;
import se.cloudshop.export.CsvEscaper;

@RestController
public class CustomerExportController {

  private final CustomerRepository customerRepository;
  private final AuthHeader authHeader;
  private final AuditService auditService;

  public CustomerExportController(CustomerRepository customerRepository, AuthHeader authHeader, AuditService auditService) {
    this.customerRepository = customerRepository;
    this.authHeader = authHeader;
    this.auditService = auditService;
  }

  @GetMapping("/customers/export")
  public ResponseEntity<byte[]> exportCustomers(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader
  ) {
    authHeader.requireValidToken(authorizationHeader);

    StringBuilder csv = new StringBuilder();
    csv.append("Id,Namn,E-post,Personnummer,Adress,Postnummer,Stad,Telefon,Arkiverad\n");

    int exportedCount = 0;
    for (Customer customer : customerRepository.findAll()) {
      csv.append(customer.getId()).append(",");
      csv.append(escape(customer.getName())).append(",");
      csv.append(escape(customer.getEmail())).append(",");
      csv.append(escape(customer.getPersonalNumber())).append(",");
      csv.append(escape(customer.getAddress())).append(",");
      csv.append(escape(customer.getPostalCode())).append(",");
      csv.append(escape(customer.getCity())).append(",");
      csv.append(escape(customer.getPhone())).append(",");
      csv.append(customer.isArchived()).append("\n");
      exportedCount++;
    }

    auditService.record(
        "export",
        "customers",
        "customers",
        "customers_exported",
        "customers",
        "Customers exported. Rows: " + exportedCount + ".",
        exportedCount,
        authorizationHeader
    );

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=customers.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String escape(String value) {
    return CsvEscaper.escape(value);
  }
}
