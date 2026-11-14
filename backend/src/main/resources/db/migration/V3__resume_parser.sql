-- Which extractor produced parsed_json: OPENAI or KEYWORD (fallback without AI).
ALTER TABLE resumes ADD COLUMN parser VARCHAR(20);
