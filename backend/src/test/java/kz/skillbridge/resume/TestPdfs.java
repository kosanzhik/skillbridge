package kz.skillbridge.resume;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

final class TestPdfs {

    private TestPdfs() {
    }

    static byte[] withText(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (lines.length > 0) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    cs.setLeading(14);
                    cs.newLineAtOffset(50, 740);
                    for (String line : lines) {
                        cs.showText(line);
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    static byte[] sampleCv() throws IOException {
        return withText(
                "Aliya Nurlanovna - Junior Accountant",
                "Almaty, Kazakhstan | Languages: Kazakh, Russian, English (B2)",
                "EXPERIENCE",
                "Junior Accountant, Steppe Trade LLP, 2022-09 - present",
                "Prepared monthly financial reporting under IFRS, reconciliations in Excel (pivot tables, VLOOKUP).",
                "Built a Power BI dashboard for receivables; wrote basic SQL queries to export data.",
                "EDUCATION",
                "Narxoz University, BSc Accounting and Audit, 2018 - 2022",
                "SKILLS: Excel, 1C, SQL (basic), Power BI, Python (coursework), Accounting");
    }
}
