package kz.skillbridge.progress;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Append-only history of user progress (spec §4 step 9). Joins the caller's transaction. */
@Component
@RequiredArgsConstructor
public class ProgressRecorder {

    public static final String ROADMAP_GENERATED = "ROADMAP_GENERATED";
    public static final String ROADMAP_ITEM_STATUS_CHANGED = "ROADMAP_ITEM_STATUS_CHANGED";
    public static final String CHALLENGE_SUBMITTED = "CHALLENGE_SUBMITTED";
    public static final String SKILL_LEVEL_CHANGED = "SKILL_LEVEL_CHANGED";

    private final ProgressEventRepository events;
    private final JsonMapper json;

    public void record(Long userId, String type, Map<String, ?> payload) {
        ProgressEvent e = new ProgressEvent();
        e.setUserId(userId);
        e.setType(type);
        e.setPayloadJson(json.writeValueAsString(payload));
        events.save(e);
    }
}
