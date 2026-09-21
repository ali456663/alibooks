package se.cloudshop.invoice;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import se.cloudshop.order.Order;
import se.cloudshop.settings.SettingsService;

@Service
public class InvoicePdfService {

  private final SettingsService settingsService;

  public InvoicePdfService(SettingsService settingsService) {
    this.settingsService = settingsService;
  }

  public byte[] createInvoicePdf(Order invoice) {
    return createInvoicePdf(invoice, false);
  }

  byte[] createInvoicePdf(Order invoice, boolean missingOriginal) {
    boolean reconstructed = missingOriginal || invoice.getDocumentSnapshot() == null;
    InvoiceDocumentSnapshot snapshot = invoice.getDocumentSnapshot() == null
        ? InvoiceDocumentSnapshot.capture(invoice, settingsService.getSettings()) : invoice.getDocumentSnapshot();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Document document = new Document();

    PdfWriter writer = PdfWriter.getInstance(document, outputStream);
    document.open();

    Color blue = new Color(21, 94, 232);
    Color yellow = new Color(255, 205, 48);
    Color dark = new Color(25, 24, 21);
    drawHeaderBackground(writer, document, blue, yellow);

    Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, Color.WHITE);
    Font companyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);
    Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, dark);
    Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
    Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);

    Paragraph brand = new Paragraph("AliBooks", brandFont);
    document.add(brand);
    Paragraph company = new Paragraph(value(snapshot.issuerName(), "-"), companyFont);
    document.add(company);
    document.add(new Paragraph(value(snapshot.issuerAddress(), "-"), companyFont));
    document.add(new Paragraph(value(snapshot.issuerPostalCode(), "") + " " + value(snapshot.issuerCity(), ""), companyFont));
    if (snapshot.issuerOrganizationNumber() != null && !snapshot.issuerOrganizationNumber().isBlank()) {
      document.add(new Paragraph("Organisationsnummer / Company registration no: " + snapshot.issuerOrganizationNumber(), companyFont));
    }
    if (snapshot.vatRegistrationNumber() != null && !snapshot.vatRegistrationNumber().isBlank()) {
      document.add(new Paragraph("Momsregistreringsnummer / VAT registration no: " + snapshot.vatRegistrationNumber(), companyFont));
    }
    document.add(new Paragraph(" "));
    document.add(new Paragraph(" "));

    String documentTitle = invoice.isCreditInvoice() ? "Kreditfaktura / Credit note"
        : "DRAFT".equals(invoice.getStatus()) ? "Fakturautkast / Draft invoice" : "Faktura / Invoice";
    Paragraph title = new Paragraph(documentTitle, titleFont);
    title.setAlignment(Element.ALIGN_RIGHT);
    document.add(title);
    if (reconstructed) {
      document.add(new Paragraph("Rekonstruerad kopia / Reconstructed copy: originaldokument eller historiska registeruppgifter saknas. Kontrollera mot originalfakturan.", normalFont));
    }
    if (invoice.isCreditInvoice()) {
      document.add(new Paragraph("Hänvisar till originalfaktura / References original invoice: "
          + value(snapshot.creditedInvoiceNumber(), "Saknas - kontrollera originalfakturan / Missing - verify original invoice"), normalFont));
    }
    document.add(new Paragraph(" "));

    PdfPTable meta = new PdfPTable(2);
    meta.setWidthPercentage(100);
    meta.getDefaultCell().setBorder(Rectangle.NO_BORDER);
    addCell(meta, "Fakturanummer / Invoice number", headingFont);
    addCell(meta, value(invoice.getInvoiceNumber(), invoice.getId() == null ? "Ej tilldelat / Not assigned" : "F-" + invoice.getId()), normalFont);
    addCell(meta, "Datum / Date", headingFont);
    addCell(meta, invoice.getInvoiceDate() == null ? "-" : invoice.getInvoiceDate().toString(), normalFont);
    if (!invoice.isCreditInvoice()) {
      addCell(meta, "Forfallodatum / Due date", headingFont);
      addCell(meta, invoice.getDueDate() == null ? "-" : invoice.getDueDate().toString(), normalFont);
      addCell(meta, "Betalningsvillkor / Payment terms", headingFont);
      addCell(meta, invoice.getPaymentTermsDays() + " dagar / days", normalFont);
    }
    addCell(meta, "Status", headingFont);
    addCell(meta, value(invoice.getStatus(), "DRAFT"), normalFont);
    document.add(meta);

    document.add(new Paragraph(" "));
    document.add(sectionTitle("Kund / Customer", headingFont));
    addCustomer(document, snapshot, normalFont);

    document.add(new Paragraph(" "));
    PdfPTable rows = new PdfPTable(6);
    rows.setWidthPercentage(100);
    rows.setWidths(new float[] {3.4f, 0.7f, 1.25f, 1.3f, 1.1f, 1.25f});
    addCell(rows, "Tjanst / Service", headingFont);
    addCell(rows, "Antal / Qty", headingFont);
    addCell(rows, "Enhetspris exkl. moms / Unit price excl. VAT", headingFont);
    addCell(rows, "Beskattningsunderlag / Tax base", headingFont);
    addCell(rows, "Moms / VAT (" + invoice.getVatPercent() + "%)", headingFont);
    addCell(rows, "Totalt / Total", headingFont);
    addCell(rows, value(snapshot.productName(), "Tjanst / Service"), normalFont);
    addCell(rows, String.valueOf(invoice.getQuantity()), normalFont);
    long unitPriceMinor = ordinaryPriceMinor(invoice) / invoice.getQuantity();
    if (invoice.isCreditInvoice()) unitPriceMinor = -unitPriceMinor;
    addCell(rows, formatSek(unitPriceMinor), normalFont);
    addCell(rows, formatSek(netAmountMinor(invoice)), normalFont);
    addCell(rows, formatSek(vatAmountMinor(invoice)), normalFont);
    addCell(rows, formatSek(totalAmountMinor(invoice)), normalFont);
    document.add(rows);

    if (discountAmountMinor(invoice) > 0) {
      PdfPTable discount = new PdfPTable(2);
      discount.setWidthPercentage(100);
      discount.setSpacingBefore(8);
      addCell(discount, "Ordinarie pris / Regular price", normalFont);
      int creditSign = invoice.isCreditInvoice() ? -1 : 1;
      addCell(discount, formatSek(creditSign * ordinaryPriceMinor(invoice)), normalFont);
      addCell(discount, "Rabatt / Discount" + discountLabelSuffix(invoice), normalFont);
      addCell(discount, formatSek(creditSign * -discountAmountMinor(invoice)), normalFont);
      document.add(discount);
    }

    document.add(new Paragraph(" "));
    if (!invoice.isCreditInvoice()) {
      document.add(sectionTitle("Betalning / Payment", headingFont));
      document.add(new Paragraph("Betalt / Paid: " + formatSek(paidAmountMinor(invoice)), normalFont));
      document.add(new Paragraph("DRAFT".equals(invoice.getStatus())
          ? "Utkast - inte betalningsunderlag / Draft - not a payment request"
          : "Att betala / Remaining: " + formatSek(invoice.getRemainingAmountMinor()), headingFont));
      document.add(new Paragraph("PlusGiro: " + value(snapshot.plusGiro(), "-"), normalFont));
      document.add(new Paragraph("OCR: " + value(snapshot.ocr(), "-"), normalFont));
      document.add(new Paragraph("Mottagare / Recipient: " + value(snapshot.paymentRecipient(), "-"), normalFont));
    } else {
      document.add(new Paragraph("Kreditbelopp / Credit amount: " + formatSek(totalAmountMinor(invoice))
          + ". Ingen betalningsbegaran / Not a payment request.", normalFont));
    }
    if (snapshot.fTaxApproved()) {
      document.add(new Paragraph("Godkand for F-skatt / Approved for F-tax", normalFont));
    }

    Paragraph footer = new Paragraph("Kontakt / Contact: " + value(snapshot.contactEmail(), "-"), footerFont);
    footer.setAlignment(Element.ALIGN_CENTER);
    footer.setSpacingBefore(36);
    document.add(footer);

    document.close();
    return outputStream.toByteArray();
  }

  private void drawHeaderBackground(PdfWriter writer, Document document, Color blue, Color yellow) {
    PdfContentByte canvas = writer.getDirectContentUnder();
    Rectangle pageSize = document.getPageSize();
    canvas.setColorFill(blue);
    canvas.rectangle(0, pageSize.getTop() - 92, pageSize.getWidth(), 92);
    canvas.fill();
    canvas.setColorFill(yellow);
    canvas.rectangle(pageSize.getRight() - 130, pageSize.getTop() - 92, 130, 92);
    canvas.fill();
  }

  private Paragraph sectionTitle(String text, Font font) {
    Paragraph paragraph = new Paragraph(text, font);
    paragraph.setSpacingBefore(8);
    paragraph.setSpacingAfter(8);
    paragraph.setIndentationLeft(0);
    return paragraph;
  }

  private void addCustomer(Document document, InvoiceDocumentSnapshot customer, Font normalFont) {
    document.add(new Paragraph(value(customer.customerName(), "-"), normalFont));
    document.add(new Paragraph("Personnummer / Personal number: " + value(customer.personalNumber(), "-"), normalFont));
    document.add(new Paragraph("E-post / Email: " + value(customer.email(), "-"), normalFont));
    document.add(new Paragraph("Tel: " + value(customer.phone(), "-"), normalFont));
    document.add(new Paragraph(value(customer.address(), "-"), normalFont));
    document.add(new Paragraph(value(customer.postalCode(), "") + " " + value(customer.city(), ""), normalFont));
  }

  private void addCell(PdfPTable table, String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setPadding(8);
    table.addCell(cell);
  }

  private String discountLabelSuffix(Order invoice) {
    String label = invoice.getDiscountLabel();
    return label == null || label.isBlank() ? "" : " (" + label + ")";
  }

  private long netAmountMinor(Order invoice) {
    return minorOrWholeKrona(invoice.getNetAmountMinor(), invoice.getNetAmount());
  }

  private long vatAmountMinor(Order invoice) {
    return minorOrWholeKrona(invoice.getVatAmountMinor(), invoice.getVatAmount());
  }

  private long totalAmountMinor(Order invoice) {
    return minorOrWholeKrona(invoice.getTotalAmountMinor(), invoice.getTotalAmount());
  }

  private long paidAmountMinor(Order invoice) {
    return minorOrWholeKrona(invoice.getPaidAmountMinor(), invoice.getPaidAmount());
  }

  private long ordinaryPriceMinor(Order invoice) {
    return minorOrWholeKrona(invoice.getOrdinaryPriceMinor(), invoice.getOrdinaryPrice());
  }

  private long discountAmountMinor(Order invoice) {
    return minorOrWholeKrona(invoice.getDiscountAmountMinor(), invoice.getDiscountAmount());
  }

  private long minorOrWholeKrona(Long minor, int wholeKrona) {
    return minor == null ? Math.multiplyExact((long) wholeKrona, 100L) : minor;
  }

  private String formatSek(long minor) {
    BigDecimal amount = BigDecimal.valueOf(minor, 2).setScale(2, RoundingMode.UNNECESSARY);
    String formatted = minor % 100L == 0L
        ? amount.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
        : amount.toPlainString();
    return formatted.replace('.', ',') + " SEK";
  }

  private String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
