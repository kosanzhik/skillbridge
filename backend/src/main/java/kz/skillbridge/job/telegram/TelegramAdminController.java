package kz.skillbridge.job.telegram;

import java.io.IOException;
import java.util.Map;
import kz.skillbridge.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/jobs/telegram")
@RequiredArgsConstructor
public class TelegramAdminController {

    private final TelegramExportReader exportReader;
    private final TelegramIngestService ingest;
    private final TelegramBotPoller poller;

    /** Imports channel history from a Telegram Desktop export (result.json). */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<TelegramIngestService.Outcome, Integer> importExport(@RequestParam("file") MultipartFile file) {
        try {
            return ingest.ingestAll(exportReader.read(file.getBytes()));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TG_EXPORT_INVALID", "Could not read file");
        }
    }

    /** Fetches new channel posts via the bot right now (also runs on a schedule when enabled). */
    @PostMapping("/poll")
    public Map<TelegramIngestService.Outcome, Integer> poll() {
        return poller.pollOnce();
    }
}
