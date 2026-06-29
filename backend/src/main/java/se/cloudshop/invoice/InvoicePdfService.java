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
import org.springframework.stereotype.Service;
import se.cloudshop.customer.Customer;
import se.cloudshop.order.Order;
import se.cloudshop.settings.AppSettings;
import se.cloudshop.settings.SettingsService;

@Service
public class InvoicePdfService {

  private final SettingsService settingsService;

  public InvoicePdfService(SettingsService settingsService) {
    this.settingsService = settingsService;
  }

  public byte[] createInvoicePdf(Order invoice) {
    AppSettings settings = settingsService.getSettings();
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
    Paragraph company = new Paragraph(value(settings.getCompanyName(), "Muscle&Focus"), companyFont);
    document.add(company);
    document.add(new Paragraph(" "));
    document.add(new Paragraph(" "));

    Paragraph title = new Paragraph("Faktura / Invoice", titleFont);
    title.setAlignment(Element.ALIGN_RIGHT);
    document.add(title);
    document.add(new Paragraph(" "));

    PdfPTable meta = new PdfPTable(2);
    meta.setWidthPercentage(100);
    meta.getDefaultCell().setBorder(Rectangle.NO_BORDER);
    addCell(meta, "Fakturanummer / Invoice number", headingFont);
    addCell(meta, value(invoice.getInvoiceNumber(), "F-" + invoice.getId()), normalFont);
    addCell(meta, "Datum / Date", headingFont);
    addCell(meta, String.valueOf(invoice.getInvoiceDate()), normalFont);
    addCell(meta, "Forfallodatum / Due date", headingFont);
    addCell(meta, String.valueOf(invoice.getDueDate()), normalFont);
    addCell(meta, "Betalningsvillkor / Payment terms", headingFont);
    addCell(meta, invoice.getPaymentTermsDays() + " dagar / days", normalFont);
    addCell(meta, "Status", headingFont);
    addCell(meta, value(invoice.getStatus(), "DRAFT"), normalFont);
    document.add(meta);

    document.add(new Paragraph(" "));
    document.add(sectionTitle("Kund / Customer", headingFont));
    addCustomer(document, invoice.getCustomer(), normalFont);

    document.add(new Paragraph(" "));
    PdfPTable rows = new PdfPTable(5);
    rows.setWidthPercentage(100);
    addCell(rows, "Tjanst / Service", headingFont);
    addCell(rows, "Antal / Qty", headingFont);
    addCell(rows, "Netto / Net", headingFont);
    addCell(rows, "Moms / VAT", headingFont);
    addCell(rows, "Totalt / Total", headingFont);
    addCell(rows, invoice.getProduct().getName(), normalFont);
    addCell(rows, String.valueOf(invoice.getQuantity()), normalFont);
    addCell(rows, invoice.getNetAmount() + " SEK", normalFont);
    addCell(rows, invoice.getVatAmount() + " SEK", normalFont);
    addCell(rows, invoice.getTotalAmount() + " SEK", normalFont);
    document.add(rows);

    if (invoice.getDiscountAmount() > 0) {
      PdfPTable discount = new PdfPTable(2);
      discount.setWidthPercentage(100);
      discount.setSpacingBefore(8);
      addCell(discount, "Ordinarie pris / Regular price", normalFont);
      addCell(discount, invoice.getOrdinaryPrice() + " SEK", normalFont);
      addCell(discount, "Rabatt / Discount" + discountLabelSuffix(invoice), normalFont);
      addCell(discount, "-" + invoice.getDiscountAmount() + " SEK", normalFont);
      document.add(discount);
    }

    document.add(new Paragraph(" "));
    document.add(sectionTitle("Betalning / Payment", headingFont));
    document.add(new Paragraph("PlusGiro: " + value(invoice.getPlusGiro(), settings.getPlusGiro()), normalFont));
    document.add(new Paragraph("OCR: " + value(invoice.getOcrNumber(), settings.getDefaultOcr()), normalFont));
    document.add(new Paragraph("Mottagare / Recipient: " + value(invoice.getPaymentRecipient(), settings.getPaymentRecipient()), normalFont));
    if (settings.isFTaxApproved()) {
      document.add(new Paragraph("Godkand for F-skatt / Approved for F-tax", normalFont));
    }

    Paragraph footer = new Paragraph("Kontakt / Contact: " + value(settings.getContactEmail(), "ali.wafa17943@gmail.com"), footerFont);
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

  private void addCustomer(Document document, Customer customer, Font normalFont) {
    if (customer == null) {
      document.add(new Paragraph("Customer information missing", normalFont));
      return;
    }

    document.add(new Paragraph(value(customer.getName(), "-"), normalFont));
    document.add(new Paragraph("Personnummer / Personal number: " + value(customer.getPersonalNumber(), "-"), normalFont));
    document.add(new Paragraph("E-post / Email: " + value(customer.getEmail(), "-"), normalFont));
    document.add(new Paragraph("Tel: " + value(customer.getPhone(), "-"), normalFont));
    document.add(new Paragraph(value(customer.getAddress(), "-"), normalFont));
    document.add(new Paragraph(value(customer.getPostalCode(), "") + " " + value(customer.getCity(), ""), normalFont));
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

  private String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
