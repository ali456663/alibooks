package se.cloudshop.email;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import se.cloudshop.payroll.PayrollPayslipEmailRequest;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

@Service
public class PayrollEmailService {

  private final JavaMailSender mailSender;
  private final SettingsService settingsService;
  private final String mailHost;
  private final String mailUsername;

  public PayrollEmailService(
      JavaMailSender mailSender,
      SettingsService settingsService,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${spring.mail.username:}") String mailUsername
  ) {
    this.mailSender = mailSender;
    this.settingsService = settingsService;
    this.mailHost = mailHost;
    this.mailUsername = mailUsername;
  }

  public void sendPayslip(PayrollPayslipEmailRequest request) {
    requireEmailConfigured();
    validateRequest(request);

    AppSettings settings = settingsService.getSettings();

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(mailUsername);
      helper.setTo(request.recipientEmail());
      helper.setReplyTo(settings.getContactEmail());
      helper.setSubject(request.subject() == null || request.subject().isBlank()
          ? "Lonebesked " + safe(request.period())
          : request.subject());
      helper.setText(request.body(), false);
      helper.addAttachment(payslipFilename(request), new ByteArrayResource(createPayslipPdf(request, settings)), "application/pdf");
      mailSender.send(message);
    } catch (MessagingException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create payslip email.");
    }
  }

  private void requireEmailConfigured() {
    if (mailHost == null || mailHost.isBlank() || mailUsername == null || mailUsername.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is not configured. Add SMTP settings first.");
    }
  }

  private void validateRequest(PayrollPayslipEmailRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payslip email request is required.");
    }

    if (request.recipientEmail() == null || request.recipientEmail().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee has no email address.");
    }

    if (!request.recipientEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee email address is invalid.");
    }

    if (request.body() == null || request.body().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payslip email body is required.");
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private byte[] createPayslipPdf(PayrollPayslipEmailRequest request, AppSettings settings) {
    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      Document document = new Document();
      PdfWriter.getInstance(document, outputStream);
      document.open();

      Color blue = new Color(21, 94, 232);
      Color dark = new Color(25, 24, 21);
      Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, blue);
      Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, dark);
      Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, dark);
      Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, dark);
      Font mutedFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);

      Paragraph brand = new Paragraph("AliBooks", brandFont);
      document.add(brand);
      document.add(new Paragraph(safe(settings.getCompanyName()).isBlank() ? "Muscle&Focus" : settings.getCompanyName(), headingFont));
      document.add(new Paragraph(" "));

      Paragraph title = new Paragraph("Lonebesked / Payslip", titleFont);
      title.setAlignment(Element.ALIGN_RIGHT);
      document.add(title);
      document.add(new Paragraph(" "));

      PdfPTable meta = new PdfPTable(2);
      meta.setWidthPercentage(100);
      meta.getDefaultCell().setBorder(Rectangle.NO_BORDER);
      addCell(meta, "Period", headingFont);
      addCell(meta, safe(request.period()), normalFont);
      addCell(meta, "Utbetalningsdatum / Payment date", headingFont);
      addCell(meta, safe(request.paymentDate()), normalFont);
      addCell(meta, "Status", headingFont);
      addCell(meta, safe(request.status()), normalFont);
      addCell(meta, "Verifikat / Voucher", headingFont);
      addCell(meta, safe(request.voucherNumber()).isBlank() ? "-" : request.voucherNumber(), normalFont);
      document.add(meta);

      document.add(new Paragraph(" "));
      document.add(sectionTitle("Anstalld / Employee", headingFont));
      document.add(new Paragraph(safe(request.employeeName()), normalFont));
      document.add(new Paragraph("Personnummer / Personal number: " + value(request.personalNumber(), "-"), normalFont));
      document.add(new Paragraph("E-post / Email: " + value(request.recipientEmail(), "-"), normalFont));
      document.add(new Paragraph("Adress / Address: " + value(request.address(), "-"), normalFont));

      document.add(new Paragraph(" "));
      document.add(sectionTitle("Loneberakning / Payroll calculation", headingFont));
      PdfPTable calculation = new PdfPTable(2);
      calculation.setWidthPercentage(100);
      addCell(calculation, "Bruttolon / Gross salary", headingFont);
      addCell(calculation, request.grossSalary() + " SEK", normalFont);
      addCell(calculation, "Preliminar skatt / Preliminary tax", headingFont);
      addCell(calculation, "-" + request.withheldTax() + " SEK", normalFont);
      addCell(calculation, "Nettolon att betala / Net pay", headingFont);
      addCell(calculation, request.netPay() + " SEK", normalFont);
      addCell(calculation, "Arbetsgivaravgift / Employer contribution", headingFont);
      addCell(calculation, request.employerFee() + " SEK", normalFont);
      addCell(calculation, "Total lonekostnad / Total payroll cost", headingFont);
      addCell(calculation, request.totalCost() + " SEK", normalFont);
      document.add(calculation);

      document.add(new Paragraph(" "));
      document.add(sectionTitle("Bokforing / Bookkeeping", headingFont));
      PdfPTable bookkeeping = new PdfPTable(3);
      bookkeeping.setWidthPercentage(100);
      addCell(bookkeeping, "Konto / Account", headingFont);
      addCell(bookkeeping, "Debet", headingFont);
      addCell(bookkeeping, "Kredit", headingFont);
      addCell(bookkeeping, value(request.salaryAccount(), "7010"), normalFont);
      addCell(bookkeeping, request.grossSalary() + " SEK", normalFont);
      addCell(bookkeeping, "-", normalFont);
      addCell(bookkeeping, "7510", normalFont);
      addCell(bookkeeping, request.employerFee() + " SEK", normalFont);
      addCell(bookkeeping, "-", normalFont);
      addCell(bookkeeping, "2710", normalFont);
      addCell(bookkeeping, "-", normalFont);
      addCell(bookkeeping, request.withheldTax() + " SEK", normalFont);
      addCell(bookkeeping, "2731", normalFont);
      addCell(bookkeeping, "-", normalFont);
      addCell(bookkeeping, request.employerFee() + " SEK", normalFont);
      addCell(bookkeeping, "1930", normalFont);
      addCell(bookkeeping, "-", normalFont);
      addCell(bookkeeping, request.netPay() + " SEK", normalFont);
      document.add(bookkeeping);

      document.add(new Paragraph(" "));
      document.add(new Paragraph("Kontrollera alltid aktuell skattetabell, avtal och Skatteverkets uppgifter innan riktig lon betalas ut.", mutedFont));
      document.add(new Paragraph("Kontakt / Contact: " + value(settings.getContactEmail(), "ali.wafa17943@gmail.com"), mutedFont));

      document.close();
      return outputStream.toByteArray();
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create payslip PDF.");
    }
  }

  private Paragraph sectionTitle(String text, Font font) {
    Paragraph paragraph = new Paragraph(text, font);
    paragraph.setSpacingBefore(8);
    paragraph.setSpacingAfter(8);
    return paragraph;
  }

  private void addCell(PdfPTable table, String text, Font font) {
    PdfPCell cell = new PdfPCell(new Paragraph(text, font));
    cell.setPadding(8);
    cell.setBorderColor(new Color(220, 229, 242));
    table.addCell(cell);
  }

  private String payslipFilename(PayrollPayslipEmailRequest request) {
    String employee = value(request.employeeName(), "anstalld").replaceAll("[^A-Za-z0-9_-]+", "-");
    String period = value(request.period(), "period").replaceAll("[^A-Za-z0-9_-]+", "-");
    return "lonebesked-" + period + "-" + employee + ".pdf";
  }

  private String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
