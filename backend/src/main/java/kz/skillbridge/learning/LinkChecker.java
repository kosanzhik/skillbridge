package kz.skillbridge.learning;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Re-verifies resource links; broken ones are hidden from roadmaps until they work again. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkChecker {

    private final LearningResourceRepository resources;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record Result(int checked, int ok, int failed) {
    }

    @Transactional
    public Result checkAll() {
        List<LearningResource> all = resources.findAll();
        int ok = 0;
        for (LearningResource r : all) {
            boolean alive = isAlive(r.getUrl());
            r.setCheckFailed(!alive);
            if (alive) {
                r.setVerifiedAt(Instant.now());
                ok++;
            } else {
                log.warn("Learning resource link failed: {}", r.getUrl());
            }
        }
        return new Result(all.size(), ok, all.size() - ok);
    }

    @Scheduled(cron = "0 0 4 * * SUN", zone = "Asia/Almaty")
    void weekly() {
        checkAll();
    }

    boolean isAlive(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (compatible; SkillBridgeLinkCheck/1.0)")
                    .GET().build();
            int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            return code < 400;
        } catch (Exception e) {
            return false;
        }
    }
}
