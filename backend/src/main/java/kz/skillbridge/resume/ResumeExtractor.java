package kz.skillbridge.resume;

import kz.skillbridge.resume.dto.ParsedResume;

interface ResumeExtractor {

    ParserType type();

    ParsedResume extract(String cvText);
}
