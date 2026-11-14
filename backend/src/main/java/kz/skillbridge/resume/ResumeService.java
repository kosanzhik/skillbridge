package kz.skillbridge.resume;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kz.skillbridge.ai.AiException;
import kz.skillbridge.ai.OpenAiClient;
import kz.skillbridge.common.ApiException;
import kz.skillbridge.resume.dto.ParsedResume;
import kz.skillbridge.resume.dto.ParsedResume.ParsedSkill;
import kz.skillbridge.resume.dto.ResumeDto;
import kz.skillbridge.skill.Skill;
import kz.skillbridge.skill.SkillNormalizer;
import kz.skillbridge.skill.SkillRepository;
import kz.skillbridge.skill.SkillSource;
import kz.skillbridge.skill.UserSkill;
import kz.skillbridge.skill.UserSkillRepository;
import kz.skillbridge.storage.FileStorage;
import kz.skillbridge.user.DataSource;
import kz.skillbridge.user.Education;
import kz.skillbridge.user.EducationRepository;
import kz.skillbridge.user.Experience;
import kz.skillbridge.user.ExperienceRepository;
import kz.skillbridge.user.UserProfile;
import kz.skillbridge.user.UserProfileRepository;
import kz.skillbridge.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * CV flow (spec §4 step 2): upload PDF → extract text → structured extraction (AI or keyword fallback)
 * → user reviews/edits → confirm persists experience, education and CV_EXTRACTED skills.
 * The AI call runs outside DB transactions.
 */
@Slf4j
@Service
public class ResumeService {

    private static final int MIN_TEXT_CHARS = 50;

    private final ResumeRepository resumes;
    private final PdfTextExtractor pdf;
    private final OpenAiClient openAi;
    private final OpenAiResumeExtractor aiExtractor;
    private final KeywordResumeExtractor keywordExtractor;
    private final SkillNormalizer normalizer;
    private final SkillRepository skills;
    private final UserSkillRepository userSkills;
    private final ExperienceRepository experiences;
    private final EducationRepository educations;
    private final UserProfileRepository profiles;
    private final FileStorage storage;
    private final JsonMapper json;
    private final TransactionTemplate tx;

    public ResumeService(ResumeRepository resumes, PdfTextExtractor pdf, OpenAiClient openAi,
                         OpenAiResumeExtractor aiExtractor, KeywordResumeExtractor keywordExtractor,
                         SkillNormalizer normalizer, SkillRepository skills, UserSkillRepository userSkills,
                         ExperienceRepository experiences, EducationRepository educations,
                         UserProfileRepository profiles, FileStorage storage, JsonMapper json,
                         PlatformTransactionManager txManager) {
        this.resumes = resumes;
        this.pdf = pdf;
        this.openAi = openAi;
        this.aiExtractor = aiExtractor;
        this.keywordExtractor = keywordExtractor;
        this.normalizer = normalizer;
        this.skills = skills;
        this.userSkills = userSkills;
        this.experiences = experiences;
        this.educations = educations;
        this.profiles = profiles;
        this.storage = storage;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
    }

    public ResumeDto upload(Long userId, MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CV_INVALID_FILE", "Could not read uploaded file");
        }
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CV_INVALID_FILE", "Only PDF files are supported");
        }
        String text = pdf.extract(bytes);
        if (text.length() < MIN_TEXT_CHARS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CV_NO_TEXT",
                    "PDF has no text layer (scanned image?). Please upload a text-based PDF.");
        }

        String key = UserService.userFilesPrefix(userId) + "/resumes/" + UUID.randomUUID() + ".pdf";
        storage.save(key, bytes);
        Resume saved = tx.execute(s -> {
            Resume r = new Resume();
            r.setUserId(userId);
            r.setOriginalFilename(safeFilename(file.getOriginalFilename()));
            r.setContentType("application/pdf");
            r.setSizeBytes((long) bytes.length);
            r.setStorageKey(key);
            r.setExtractedText(text);
            r.setParseStatus(ParseStatus.PARSING);
            return resumes.save(r);
        });
        return parse(saved.getId(), saved.getExtractedText());
    }

    public ResumeDto reparse(Long userId, Long resumeId) {
        Resume r = owned(userId, resumeId);
        if (r.getExtractedText() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "CV_NO_TEXT", "Resume has no extracted text");
        }
        return parse(r.getId(), r.getExtractedText());
    }

    public List<ResumeDto> list(Long userId) {
        return resumes.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    public ResumeDto get(Long userId, Long resumeId) {
        return toDto(owned(userId, resumeId));
    }

    public byte[] file(Long userId, Long resumeId) {
        return storage.load(owned(userId, resumeId).getStorageKey());
    }

    /** Persists user-reviewed data, replacing anything previously extracted from a CV. */
    public ResumeDto confirm(Long userId, Long resumeId, ParsedResume edited) {
        ParsedResume cleaned = normalizeSkills(ResumeSanitizer.clean(edited));
        Resume result = tx.execute(s -> {
            Resume r = owned(userId, resumeId);
            if (r.getParseStatus() != ParseStatus.PARSED && r.getParseStatus() != ParseStatus.CONFIRMED) {
                throw new ApiException(HttpStatus.CONFLICT, "CV_NOT_PARSED", "Resume is not parsed yet");
            }
            replaceCvData(userId, cleaned);
            fillEmptyProfileFields(userId, cleaned);
            resumes.findByUserIdAndParseStatus(userId, ParseStatus.CONFIRMED).stream()
                    .filter(o -> !o.getId().equals(r.getId()))
                    .forEach(o -> o.setParseStatus(ParseStatus.PARSED));
            r.setParsedJson(json.writeValueAsString(cleaned));
            r.setParseStatus(ParseStatus.CONFIRMED);
            r.setConfirmedAt(Instant.now());
            return r;
        });
        return toDto(result);
    }

    /** Deletes the document; data the user already confirmed stays in the profile. */
    public void delete(Long userId, Long resumeId) {
        tx.executeWithoutResult(s -> {
            Resume r = owned(userId, resumeId);
            resumes.delete(r);
            String key = r.getStorageKey();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.delete(key);
                }
            });
        });
    }

    // ---------------------------------------------------------------------------------------------

    private ResumeDto parse(Long resumeId, String text) {
        ParserType parser;
        String note = null;
        ParsedResume parsed;
        try {
            if (openAi.isEnabled()) {
                try {
                    parsed = aiExtractor.extract(text);
                    parser = ParserType.OPENAI;
                } catch (AiException e) {
                    log.warn("AI CV parsing failed for resume {}: {}", resumeId, e.getMessage());
                    parsed = keywordExtractor.extract(text);
                    parser = ParserType.KEYWORD;
                    note = "AI parsing unavailable, skills were matched by keywords. Please fill in experience manually.";
                }
            } else {
                parsed = keywordExtractor.extract(text);
                parser = ParserType.KEYWORD;
            }
            parsed = normalizeSkills(ResumeSanitizer.clean(parsed));
        } catch (RuntimeException e) {
            log.error("CV parsing failed for resume {}", resumeId, e);
            return toDto(tx.execute(s -> {
                Resume r = resumes.findById(resumeId).orElseThrow();
                r.setParseStatus(ParseStatus.FAILED);
                r.setParseError("Parsing failed");
                return r;
            }));
        }
        ParsedResume finalParsed = parsed;
        ParserType finalParser = parser;
        String finalNote = note;
        return toDto(tx.execute(s -> {
            Resume r = resumes.findById(resumeId).orElseThrow();
            r.setParsedJson(json.writeValueAsString(finalParsed));
            r.setParser(finalParser);
            r.setParseError(finalNote);
            r.setParseStatus(ParseStatus.PARSED);
            r.setConfirmedAt(null);
            return r;
        }));
    }

    /** Attaches catalog matches; an explicit, existing skillId from the client wins over name matching. */
    private ParsedResume normalizeSkills(ParsedResume r) {
        List<ParsedSkill> out = new ArrayList<>();
        for (ParsedSkill s : r.skills()) {
            Optional<Skill> match = Optional.ofNullable(s.skillId()).flatMap(skills::findById)
                    .or(() -> normalizer.match(s.name()));
            out.add(new ParsedSkill(s.name(), s.level(), s.evidence(),
                    match.map(Skill::getId).orElse(null), match.map(Skill::getName).orElse(null)));
        }
        return new ParsedResume(r.fullName(), r.headline(), r.location(), r.languages(), r.experiences(),
                r.educations(), r.projects(), r.certifications(), out);
    }

    private void replaceCvData(Long userId, ParsedResume p) {
        experiences.deleteByUserIdAndSource(userId, DataSource.CV_EXTRACTED);
        educations.deleteByUserIdAndSource(userId, DataSource.CV_EXTRACTED);
        userSkills.deleteByUserIdAndSource(userId, SkillSource.CV_EXTRACTED);

        for (ParsedResume.Experience e : p.experiences()) {
            Experience x = new Experience();
            x.setUserId(userId);
            x.setCompany(e.company());
            x.setTitle(e.title());
            x.setStartDate(toDate(e.startDate()));
            x.setEndDate(toDate(e.endDate()));
            x.setDescription(e.description());
            x.setSource(DataSource.CV_EXTRACTED);
            experiences.save(x);
        }
        for (ParsedResume.Education e : p.educations()) {
            Education x = new Education();
            x.setUserId(userId);
            x.setInstitution(e.institution());
            x.setDegree(e.degree());
            x.setField(e.field());
            x.setStartYear(e.startYear() == null ? null : e.startYear().shortValue());
            x.setEndYear(e.endYear() == null ? null : e.endYear().shortValue());
            x.setSource(DataSource.CV_EXTRACTED);
            educations.save(x);
        }
        // several CV names can map to one catalog skill: keep the strongest
        Map<Long, ParsedSkill> bySkill = new LinkedHashMap<>();
        for (ParsedSkill s : p.skills()) {
            if (s.skillId() != null) {
                bySkill.merge(s.skillId(), s, (a, b) -> a.level() >= b.level() ? a : b);
            }
        }
        bySkill.forEach((skillId, s) -> {
            UserSkill us = new UserSkill();
            us.setUserId(userId);
            us.setSkill(skills.getReferenceById(skillId));
            us.setLevel(s.level().shortValue());
            us.setSource(SkillSource.CV_EXTRACTED);
            us.setEvidence(s.evidence());
            userSkills.save(us);
        });
    }

    private void fillEmptyProfileFields(Long userId, ParsedResume p) {
        UserProfile profile = profiles.findById(userId).orElseThrow(() -> ApiException.notFound("Profile"));
        if (profile.getFullName() == null) {
            profile.setFullName(p.fullName());
        }
        if (profile.getHeadline() == null) {
            profile.setHeadline(p.headline());
        }
        if (profile.getLocation() == null) {
            profile.setLocation(p.location());
        }
        if (profile.getLanguageList().isEmpty()) {
            profile.setLanguageList(p.languages());
        }
    }

    private Resume owned(Long userId, Long resumeId) {
        return resumes.findByIdAndUserId(resumeId, userId).orElseThrow(() -> ApiException.notFound("Resume"));
    }

    private ResumeDto toDto(Resume r) {
        ParsedResume parsed = r.getParsedJson() == null ? null : json.readValue(r.getParsedJson(), ParsedResume.class);
        List<String> unmatched = parsed == null ? List.of()
                : parsed.skills().stream().filter(s -> s.skillId() == null).map(ParsedSkill::name).toList();
        return new ResumeDto(r.getId(), r.getOriginalFilename(), r.getSizeBytes(), r.getParseStatus(), r.getParser(),
                r.getParseError(), r.getCreatedAt(), r.getConfirmedAt(), parsed, unmatched);
    }

    private static LocalDate toDate(String yearMonth) {
        return yearMonth == null ? null : LocalDate.parse(yearMonth + "-01");
    }

    private static String safeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "cv.pdf";
        }
        String base = name.replaceAll("^.*[/\\\\]", "").replaceAll("[\\p{Cntrl}]", "");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
