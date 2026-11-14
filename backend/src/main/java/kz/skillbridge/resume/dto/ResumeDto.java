package kz.skillbridge.resume.dto;

import java.time.Instant;
import java.util.List;
import kz.skillbridge.resume.ParseStatus;
import kz.skillbridge.resume.ParserType;

public record ResumeDto(
        Long id,
        String originalFilename,
        long sizeBytes,
        ParseStatus status,
        ParserType parser,
        String parseError,
        Instant createdAt,
        Instant confirmedAt,
        ParsedResume parsed,
        List<String> unmatchedSkills) {
}
