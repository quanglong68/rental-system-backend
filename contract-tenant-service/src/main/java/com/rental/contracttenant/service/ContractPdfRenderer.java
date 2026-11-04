package com.rental.contracttenant.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractSignature;
import com.rental.contracttenant.entity.ContractTenant;
import com.rental.contracttenant.entity.SignatureParty;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Renders the Vietnamese rental contract template ({@code mau-hop-dong-nha-tro-cho-thue.png}) to PDF.
 * Noto Sans is embedded so Vietnamese diacritics survive on any machine.
 */
@Component
public class ContractPdfRenderer {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String regularFontResource;
    private final String boldFontResource;

    private Font regular;
    private Font bold;
    private Font small;
    private Font title;
    private Font section;

    public ContractPdfRenderer(@Value("${contract.pdf.font-regular}") String regularFontResource,
            @Value("${contract.pdf.font-bold}") String boldFontResource) {
        this.regularFontResource = regularFontResource;
        this.boldFontResource = boldFontResource;
    }

    @PostConstruct
    void loadFonts() {
        try {
            BaseFont regularBase = BaseFont.createFont(extract(regularFontResource), BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED);
            BaseFont boldBase = BaseFont.createFont(extract(boldFontResource), BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED);
            regular = new Font(regularBase, 10.5f);
            bold = new Font(boldBase, 10.5f);
            small = new Font(regularBase, 9f);
            title = new Font(boldBase, 16f);
            section = new Font(boldBase, 11.5f);
        } catch (Exception ex) {
            throw new IllegalStateException("Không nạp được font Unicode cho PDF hợp đồng", ex);
        }
    }

    public byte[] render(Contract contract, List<ContractTenant> tenants,
            Map<SignatureParty, SignatureView> signatures) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 48, 48, 48, 56);
            PdfWriter.getInstance(document, out);
            document.addTitle("Hợp đồng thuê phòng trọ #" + contract.getId());
            document.addAuthor("Rental System");
            document.open();

            addHeader(document, contract);
            addParties(document, contract, tenants);
            addRoomAndTerms(document, contract);
            addArticles(document, contract);
            addSignatures(document, contract, tenants, signatures);
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không tạo được PDF hợp đồng " + contract.getId(), ex);
        }
    }

    /** Annex for a member joining an active contract; terms of the main contract stay unchanged. */
    public byte[] renderAddendum(Contract contract, List<ContractTenant> tenants, ContractTenant newMember) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 48, 48, 48, 56);
            PdfWriter.getInstance(document, out);
            document.addTitle("Phụ lục hợp đồng thuê phòng trọ #" + contract.getId());
            document.addAuthor("Rental System");
            document.open();

            Paragraph nation = new Paragraph("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", bold);
            nation.setAlignment(Element.ALIGN_CENTER);
            document.add(nation);
            Paragraph motto = new Paragraph("Độc lập – Tự do – Hạnh phúc", regular);
            motto.setAlignment(Element.ALIGN_CENTER);
            document.add(motto);
            Paragraph dash = new Paragraph("---------------o0o---------------", small);
            dash.setAlignment(Element.ALIGN_CENTER);
            dash.setSpacingAfter(14f);
            document.add(dash);

            Paragraph titleParagraph = new Paragraph("PHỤ LỤC HỢP ĐỒNG THUÊ PHÒNG TRỌ", title);
            titleParagraph.setAlignment(Element.ALIGN_CENTER);
            titleParagraph.setSpacingAfter(4f);
            document.add(titleParagraph);
            Paragraph number = new Paragraph("Số: PL-" + contract.getId() + "-" + (newMember.getId() == null ? "" : newMember.getId()), regular);
            number.setAlignment(Element.ALIGN_CENTER);
            number.setSpacingAfter(12f);
            document.add(number);

            document.add(new Paragraph("Kèm theo Hợp đồng số HĐ-" + contract.getId()
                    + " về việc thuê phòng " + safe(contract.getRoomNumber())
                    + " tại " + safe(contract.getBuildingName()) + ".", regular));
            document.add(new Paragraph(" ", small));

            document.add(section("BÊN A (BÊN CHO THUÊ)"));
            document.add(new Paragraph(safe(contract.getLessorName()) + " – SĐT: " + safe(contract.getLessorPhone()), regular));
            document.add(new Paragraph("Địa chỉ: " + safe(contract.getLessorAddress()), regular));
            document.add(new Paragraph(" ", small));

            document.add(section("BÊN B (BÊN THUÊ)"));
            ContractTenant representative = tenants.stream().filter(ContractTenant::isRepresentative).findFirst()
                    .orElse(null);
            document.add(new Paragraph("Người đại diện: "
                    + safe(representative == null ? contract.getTenantFullName() : representative.getFullName())
                    + " – CCCD: " + safe(representative == null ? contract.getTenantCccdNumber() : representative.getCccdNumber()), regular));
            document.add(new Paragraph(" ", small));

            document.add(section("NỘI DUNG PHỤ LỤC"));
            document.add(new Paragraph("Bổ sung ông/bà " + safe(newMember.getFullName())
                    + " – CCCD: " + safe(newMember.getCccdNumber())
                    + " – SĐT: " + safe(newMember.getPhone())
                    + " vào danh sách người ở của phòng, bắt đầu từ ngày "
                    + (newMember.getJoinedAt() == null ? "—" : DATE.format(newMember.getJoinedAt())) + ".", regular));
            document.add(new Paragraph("Người được bổ sung là người ở (không phải người đại diện ký hợp đồng). "
                    + "Tiền phòng, điện, nước và dịch vụ chung được chia theo số người ở thực tế và số ngày có mặt "
                    + "trong tháng, theo quy định của hợp đồng chính.", regular));
            document.add(new Paragraph("Các điều khoản khác của Hợp đồng số HĐ-" + contract.getId()
                    + " không thay đổi và tiếp tục có hiệu lực.", regular));
            document.add(new Paragraph(" ", small));

            PdfPTable signatures = new PdfPTable(2);
            signatures.setWidthPercentage(100);
            signatures.setSpacingBefore(24f);
            signatures.addCell(signatureCell("BÊN A\n(Ký, ghi rõ họ tên)"));
            signatures.addCell(signatureCell("BÊN B – NGƯỜI ĐẠI DIỆN\n(Ký, ghi rõ họ tên)"));
            document.add(signatures);

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không tạo được phụ lục hợp đồng " + contract.getId(), ex);
        }
    }

    private PdfPCell signatureCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, small));
        cell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setMinimumHeight(80f);
        return cell;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    /** Older representative rows may predate the identity snapshot; fall back to the contract copy. */
    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static LocalDate firstNonNull(LocalDate preferred, LocalDate fallback) {
        return preferred != null ? preferred : fallback;
    }

    private void addHeader(Document document, Contract contract) {
        Paragraph nation = new Paragraph("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", bold);
        nation.setAlignment(Element.ALIGN_CENTER);
        document.add(nation);
        Paragraph motto = new Paragraph("Độc lập – Tự do – Hạnh phúc", regular);
        motto.setAlignment(Element.ALIGN_CENTER);
        document.add(motto);
        Paragraph dash = new Paragraph("---------------o0o---------------", small);
        dash.setAlignment(Element.ALIGN_CENTER);
        dash.setSpacingAfter(14f);
        document.add(dash);

        Paragraph titleParagraph = new Paragraph("HỢP ĐỒNG THUÊ PHÒNG TRỌ", title);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        titleParagraph.setSpacingAfter(4f);
        document.add(titleParagraph);

        Paragraph number = new Paragraph("Số: HĐ-" + contract.getId(), regular);
        number.setAlignment(Element.ALIGN_CENTER);
        number.setSpacingAfter(4f);
        document.add(number);

        Paragraph date = new Paragraph("Hôm nay, ngày " + LocalDate.now(VN).format(DATE)
                + ", các bên cùng thống nhất ký kết hợp đồng thuê phòng trọ với nội dung sau:", regular);
        date.setSpacingAfter(10f);
        document.add(date);
    }

    private void addParties(Document document, Contract contract, List<ContractTenant> tenants) {
        document.add(section("BÊN A – BÊN CHO THUÊ (NGƯỜI ĐẠI DIỆN)"));
        document.add(line("Họ và tên", contract.getLessorName()));
        document.add(line("Ngày sinh", date(contract.getLessorDateOfBirth())));
        document.add(line("Số CCCD/CMND", contract.getLessorIdNumber()));
        document.add(line("Ngày cấp", date(contract.getLessorIdIssueDate())));
        document.add(line("Nơi cấp", contract.getLessorIdIssuePlace()));
        document.add(line("Điện thoại", contract.getLessorPhone()));
        document.add(line("Địa chỉ", contract.getLessorAddress()));
        document.add(spacer());

        document.add(section("BÊN B – BÊN THUÊ (NGƯỜI ĐẠI DIỆN)"));
        ContractTenant representative = tenants == null ? null : tenants.stream()
                .filter(t -> t.isRepresentative() && t.getLeftAt() == null).findFirst().orElse(null);
        document.add(line("Họ và tên", representative != null
                ? firstNonBlank(representative.getFullName(), contract.getTenantFullName())
                : contract.getTenantFullName()));
        document.add(line("Ngày sinh", date(representative != null
                ? firstNonNull(representative.getDateOfBirth(), contract.getTenantDateOfBirth())
                : contract.getTenantDateOfBirth())));
        document.add(line("Giới tính", representative != null
                ? firstNonBlank(representative.getGender(), contract.getTenantGender())
                : contract.getTenantGender()));
        document.add(line("Số CCCD/CMND", representative != null
                ? firstNonBlank(representative.getCccdNumber(), contract.getTenantCccdNumber())
                : contract.getTenantCccdNumber()));
        document.add(line("Ngày cấp", date(representative != null
                ? firstNonNull(representative.getCccdIssueDate(), contract.getTenantCccdIssueDate())
                : contract.getTenantCccdIssueDate())));
        document.add(line("Nơi cấp", representative != null
                ? firstNonBlank(representative.getCccdIssuePlace(), contract.getTenantCccdIssuePlace())
                : contract.getTenantCccdIssuePlace()));
        document.add(line("Điện thoại", representative != null
                ? firstNonBlank(representative.getPhone(), contract.getTenantPhone())
                : contract.getTenantPhone()));
        document.add(line("Email", representative != null
                ? firstNonBlank(representative.getEmail(), contract.getTenantEmail())
                : contract.getTenantEmail()));
        document.add(line("Địa chỉ thường trú", representative != null
                ? firstNonBlank(representative.getPermanentAddress(), contract.getTenantPermanentAddress())
                : contract.getTenantPermanentAddress()));
        document.add(line("Nghề nghiệp", representative != null
                ? firstNonBlank(representative.getOccupation(), contract.getTenantOccupation())
                : contract.getTenantOccupation()));
        document.add(spacer());
    }

    private void addRoomAndTerms(Document document, Contract contract) {
        document.add(section("ĐIỀU 1 – PHÒNG THUÊ VÀ THỜI HẠN"));
        document.add(line("Phòng", contract.getRoomNumber() != null ? contract.getRoomNumber()
                : "Phòng #" + contract.getRoomId()));
        document.add(line("Tòa nhà", contract.getBuildingName()));
        document.add(line("Địa chỉ", contract.getRoomAddress()));
        document.add(line("Thời hạn thuê", date(contract.getStartDate()) + " – " + date(contract.getEndDate())));
        document.add(line("Ngày có thể vào ở", date(contract.getAvailableFrom())));
        document.add(spacer());

        document.add(section("ĐIỀU 2 – GIÁ THUÊ, ĐẶT CỌC VÀ THANH TOÁN"));
        document.add(line("Giá thuê hàng tháng", money(contract.getRentAmount())));
        document.add(line("Tiền đặt cọc", money(contract.getDepositAmount())));
        document.add(line("Tiền điện", money(contract.getElectricityPrice()) + " / kWh"));
        document.add(line("Tiền nước", money(contract.getWaterPrice()) + " / " + waterUnit(contract.getWaterUnit())));
        document.add(line("Hình thức thanh toán", paymentMethod(contract.getPaymentMethod())));
        document.add(line("Hạn thanh toán", contract.getPaymentDueDay() == null ? "—"
                : "Ngày " + contract.getPaymentDueDay() + " hàng tháng"));
        document.add(line("Kỳ hạn tối thiểu", contract.getMinTermMonths() == null ? "—"
                : contract.getMinTermMonths() + " tháng"));
        document.add(spacer());
    }

    private void addArticles(Document document, Contract contract) {
        document.add(section("ĐIỀU 3 – QUYỀN VÀ NGHĨA VỤ CỦA CÁC BÊN"));
        document.add(paragraph("1. Bên A bàn giao phòng đúng thời hạn, bảo đảm phòng sử dụng được và giữ nguyên "
                + "giá thuê trong thời hạn hợp đồng."));
        document.add(paragraph("2. Bên B thanh toán đầy đủ, đúng hạn; giữ gìn tài sản và không tự ý chuyển nhượng, "
                + "cho người khác thuê lại khi chưa được Bên A đồng ý."));
        document.add(paragraph("3. Bên B chịu trách nhiệm về nhân thân của người ở cùng; mọi thay đổi người ở phải "
                + "được Bên A xác nhận bằng phụ lục hợp đồng."));
        document.add(paragraph("4. Trước khi hợp đồng hết hạn, Bên B có thể đề nghị gia hạn hoặc trả phòng sớm theo "
                + "quy trình của hệ thống; việc trả phòng sớm cần Bên A chấp thuận."));
        document.add(paragraph("5. Khi hết hạn mà không gia hạn, hợp đồng chấm dứt; Bên B bàn giao phòng và thanh toán "
                + "các khoản còn lại. Tiền cọc được hoàn trả sau khi khấu trừ hư hỏng (nếu có)."));
        document.add(paragraph("6. Hai bên cam kết thực hiện đúng hợp đồng; tranh chấp được giải quyết bằng thương "
                + "lượng, nếu không thành sẽ chuyển cơ quan có thẩm quyền theo pháp luật."));
        if (contract.getMinTermMonths() != null) {
            document.add(paragraph("7. Hợp đồng có kỳ hạn tối thiểu " + contract.getMinTermMonths()
                    + " tháng kể từ ngày bắt đầu thuê."));
        }
    }

    private void addSignatures(Document document, Contract contract, List<ContractTenant> tenants,
            Map<SignatureParty, SignatureView> signatures) {
        document.add(spacer());
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] {1f, 1f});
        table.addCell(signatureCell("BÊN A", "Bên cho thuê", contract.getLessorName(),
                signatures == null ? null : signatures.get(SignatureParty.LESSOR)));
        ContractTenant representative = tenants == null ? null : tenants.stream()
                .filter(t -> t.isRepresentative() && t.getLeftAt() == null).findFirst().orElse(null);
        String tenantSignerName = contract.getTenantFullName();
        if ((tenantSignerName == null || tenantSignerName.isBlank()) && representative != null) {
            tenantSignerName = representative.getFullName();
        }
        if (tenantSignerName == null || tenantSignerName.isBlank()) {
            tenantSignerName = "Bên thuê";
        }
        table.addCell(signatureCell("BÊN B", "Bên thuê", tenantSignerName,
                signatures == null ? null : signatures.get(SignatureParty.TENANT)));
        document.add(table);
        Paragraph note = new Paragraph("Hợp đồng được tạo và ký điện tử trên hệ thống quản lý phòng trọ. "
                + "Mỗi chữ ký lưu thời điểm, địa chỉ IP, thiết bị và mã băm SHA-256 của tệp PDF tương ứng.",
                small);
        note.setSpacingBefore(10f);
        document.add(note);
    }

    private PdfPCell signatureCell(String heading, String role, String name, SignatureView signature) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        Paragraph h = new Paragraph(heading, bold);
        h.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(h);
        Paragraph r = new Paragraph(role, small);
        r.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(r);
        if (signature != null && signature.image() != null && signature.image().length > 0) {
            try {
                Image image = Image.getInstance(signature.image());
                image.scaleToFit(140f, 70f);
                image.setAlignment(Element.ALIGN_CENTER);
                cell.addElement(image);
            } catch (Exception ignored) {
                // A corrupt signature image must not block the contract; the audit row still exists.
            }
        } else {
            cell.addElement(new Paragraph("\n\n", regular));
        }
        Paragraph signed = new Paragraph(signature == null ? "Chưa ký"
                : "Đã ký điện tử " + DATE_TIME.format(signature.signedAt().atZone(VN)), small);
        signed.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(signed);
        Paragraph displayName = new Paragraph(name == null ? "" : name, bold);
        displayName.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(displayName);
        return cell;
    }

    private Paragraph section(String text) {
        Paragraph paragraph = new Paragraph(text, section);
        paragraph.setSpacingBefore(6f);
        paragraph.setSpacingAfter(4f);
        return paragraph;
    }

    private Paragraph line(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Phrase(label + ": ", bold));
        paragraph.add(new Phrase(value == null || value.isBlank() ? "—" : value, regular));
        paragraph.setSpacingAfter(2f);
        return paragraph;
    }

    private Paragraph paragraph(String text) {
        Paragraph paragraph = new Paragraph(text, regular);
        paragraph.setSpacingAfter(3f);
        paragraph.setLeading(15f);
        return paragraph;
    }

    private Paragraph spacer() {
        return new Paragraph(" ", regular);
    }

    private static String date(LocalDate date) {
        return date == null ? "—" : date.format(DATE);
    }

    private static String money(BigDecimal amount) {
        if (amount == null) return "—";
        return String.format("%,d", amount.longValue()).replace(',', '.') + " đ";
    }

    private static String waterUnit(String unit) {
        if (unit == null) return "đơn vị";
        return switch (unit) {
            case "M3" -> "m³";
            case "PERSON" -> "người";
            default -> unit;
        };
    }

    private static String paymentMethod(String method) {
        if (method == null) return "—";
        return switch (method) {
            case "CASH" -> "Tiền mặt";
            case "BANK_TRANSFER" -> "Chuyển khoản";
            default -> method;
        };
    }

    private static String extract(String classpath) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpath);
        Path temp = Files.createTempFile("contract-font-", ".ttf");
        temp.toFile().deleteOnExit();
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return temp.toAbsolutePath().toString();
    }

    public record SignatureView(Instant signedAt, byte[] image) {
    }

    /** Convenience factory used by the service before rendering. */
    public static Map<SignatureParty, SignatureView> signatureViews(List<ContractSignature> signatures,
            java.util.function.Function<String, byte[]> imageLoader) {
        Map<SignatureParty, SignatureView> views = new EnumMap<>(SignatureParty.class);
        if (signatures == null) {
            return views;
        }
        for (ContractSignature signature : signatures) {
            views.put(signature.getParty(), new SignatureView(signature.getSignedAt(),
                    imageLoader.apply(signature.getImageKey())));
        }
        return views;
    }
}
