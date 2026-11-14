package kz.skillbridge.resume;

import java.io.IOException;
import kz.skillbridge.common.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class PdfTextExtractor {

    static final int MAX_PAGES = 15;
    static final int MAX_CHARS = 40_000;

    String extract(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.getNumberOfPages() > MAX_PAGES) {
                throw unprocessable("CV_TOO_LONG", "CV must not exceed " + MAX_PAGES + " pages");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc)
                    .replace("\u0000", "")
                    .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                    .replaceAll("\\n{3,}", "\n\n")
                    .strip();
            return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        } catch (InvalidPasswordException e) {
            throw unprocessable("CV_ENCRYPTED", "PDF is password-protected");
        } catch (IOException e) {
            throw unprocessable("CV_UNREADABLE", "PDF could not be read");
        }
    }

    private static ApiException unprocessable(String code, String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, msg);
    }
}
