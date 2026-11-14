-- SkillBridge initial schema (spec §9)
CREATE EXTENSION IF NOT EXISTS vector;

-- ============ Auth / Users ============
CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    role                VARCHAR(20)  NOT NULL DEFAULT 'USER',
    is_demo             BOOLEAN      NOT NULL DEFAULT FALSE,
    consent_accepted_at TIMESTAMPTZ  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_profiles (
    user_id       BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    full_name     VARCHAR(255),
    location      VARCHAR(255),
    headline      VARCHAR(255),
    languages     VARCHAR(500),
    desired_level VARCHAR(20),   -- INTERN, JUNIOR, MIDDLE, SENIOR, LEAD
    work_format   VARCHAR(20),   -- OFFICE, HYBRID, REMOTE, ANY
    onboarded_at  TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============ Resume / Experience ============
CREATE TABLE resumes (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    storage_key       VARCHAR(500) NOT NULL,
    extracted_text    TEXT,
    parse_status      VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED', -- UPLOADED, PARSING, PARSED, CONFIRMED, FAILED
    parsed_json       TEXT,
    parse_error       TEXT,
    confirmed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_resumes_user ON resumes (user_id);

CREATE TABLE experiences (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company     VARCHAR(255),
    title       VARCHAR(255) NOT NULL,
    start_date  DATE,
    end_date    DATE,
    description TEXT,
    source      VARCHAR(20)  NOT NULL DEFAULT 'SELF_REPORTED', -- SELF_REPORTED, CV_EXTRACTED
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_experiences_user ON experiences (user_id);

CREATE TABLE educations (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    institution VARCHAR(255) NOT NULL,
    degree      VARCHAR(255),
    field       VARCHAR(255),
    start_year  SMALLINT,
    end_year    SMALLINT,
    source      VARCHAR(20)  NOT NULL DEFAULT 'SELF_REPORTED',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_educations_user ON educations (user_id);

-- ============ Skills catalog ============
CREATE TABLE skills (
    id        BIGSERIAL PRIMARY KEY,
    slug      VARCHAR(100) NOT NULL UNIQUE,
    name      VARCHAR(150) NOT NULL,
    category  VARCHAR(50)  NOT NULL, -- TOOL, LANGUAGE, DOMAIN, METHOD, SOFT
    aliases   TEXT,                  -- comma-separated synonyms for normalization
    embedding vector(1536)
);

-- Level 0..5. Source distinguishes claims from proven skills (spec §4 step 6).
CREATE TABLE user_skills (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    skill_id   BIGINT      NOT NULL REFERENCES skills (id),
    level      SMALLINT    NOT NULL CHECK (level BETWEEN 0 AND 5),
    source     VARCHAR(20) NOT NULL, -- SELF_REPORTED, CV_EXTRACTED, ASSESSED, VERIFIED
    evidence   TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, skill_id, source)
);
CREATE INDEX idx_user_skills_user ON user_skills (user_id);

-- ============ Career ============
CREATE TABLE professions (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    title       VARCHAR(150) NOT NULL,
    description TEXT,
    hh_query    VARCHAR(255) -- search text used for hh.kz vacancy import
);

-- Baseline expectations; real weights get recomputed from fetched vacancies.
CREATE TABLE profession_skills (
    profession_id  BIGINT   NOT NULL REFERENCES professions (id) ON DELETE CASCADE,
    skill_id       BIGINT   NOT NULL REFERENCES skills (id),
    required_level SMALLINT NOT NULL CHECK (required_level BETWEEN 1 AND 5),
    importance     SMALLINT NOT NULL CHECK (importance BETWEEN 1 AND 5),
    PRIMARY KEY (profession_id, skill_id)
);

CREATE TABLE career_goals (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    profession_id BIGINT      NOT NULL REFERENCES professions (id),
    is_primary    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, profession_id)
);
CREATE UNIQUE INDEX uq_career_goals_primary ON career_goals (user_id) WHERE is_primary;

-- ============ Jobs ============
CREATE TABLE jobs (
    id              BIGSERIAL PRIMARY KEY,
    source          VARCHAR(30)  NOT NULL, -- HH, TELEGRAM
    external_id     VARCHAR(100) NOT NULL,
    profession_id   BIGINT REFERENCES professions (id),
    title           VARCHAR(255) NOT NULL,
    company         VARCHAR(255),
    location        VARCHAR(255),
    salary_from     INTEGER,
    salary_to       INTEGER,
    salary_currency VARCHAR(10),
    salary_gross    BOOLEAN,
    experience      VARCHAR(100),
    employment      VARCHAR(100),
    schedule        VARCHAR(100),
    description     TEXT,
    url             VARCHAR(500) NOT NULL,
    published_at    TIMESTAMPTZ,
    fetched_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (source, external_id)
);
CREATE INDEX idx_jobs_profession ON jobs (profession_id);

CREATE TABLE job_skills (
    job_id           BIGINT      NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    skill_id         BIGINT      NOT NULL REFERENCES skills (id),
    requirement_type VARCHAR(20) NOT NULL, -- REQUIRED, PREFERRED
    PRIMARY KEY (job_id, skill_id)
);

CREATE TABLE job_matches (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id           BIGINT       NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    score            NUMERIC(5,2) NOT NULL,
    explanation_json TEXT         NOT NULL, -- matched / missing skills, formula breakdown
    computed_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, job_id)
);

-- ============ Learning ============
CREATE TABLE learning_resources (
    id               BIGSERIAL PRIMARY KEY,
    skill_id         BIGINT       NOT NULL REFERENCES skills (id),
    title            VARCHAR(255) NOT NULL,
    provider         VARCHAR(150) NOT NULL,
    url              VARCHAR(500) NOT NULL UNIQUE,
    type             VARCHAR(20)  NOT NULL, -- COURSE, VIDEO, ARTICLE, DOCS, BOOK
    level            SMALLINT,
    duration_minutes INTEGER,
    language         VARCHAR(10),
    is_free          BOOLEAN,
    verified_at      TIMESTAMPTZ -- last time link was checked to be alive
);
CREATE INDEX idx_learning_resources_skill ON learning_resources (skill_id);

CREATE TABLE roadmaps (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    career_goal_id BIGINT      NOT NULL REFERENCES career_goals (id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, ARCHIVED, COMPLETED
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE roadmap_items (
    id                  BIGSERIAL PRIMARY KEY,
    roadmap_id          BIGINT       NOT NULL REFERENCES roadmaps (id) ON DELETE CASCADE,
    skill_id            BIGINT REFERENCES skills (id),
    position            INTEGER      NOT NULL,
    title               VARCHAR(255) NOT NULL,
    goal                TEXT,
    topics              TEXT,
    estimated_hours     INTEGER,
    completion_criteria TEXT,
    rationale           TEXT, -- why this item exists (link to vacancy demand)
    status              VARCHAR(20)  NOT NULL DEFAULT 'TODO', -- TODO, IN_PROGRESS, DONE
    completed_at        TIMESTAMPTZ
);
CREATE INDEX idx_roadmap_items_roadmap ON roadmap_items (roadmap_id);

CREATE TABLE roadmap_item_resources (
    roadmap_item_id BIGINT NOT NULL REFERENCES roadmap_items (id) ON DELETE CASCADE,
    resource_id     BIGINT NOT NULL REFERENCES learning_resources (id) ON DELETE CASCADE,
    PRIMARY KEY (roadmap_item_id, resource_id)
);

-- ============ Assessment ============
CREATE TABLE challenges (
    id          BIGSERIAL PRIMARY KEY,
    skill_id    BIGINT       NOT NULL REFERENCES skills (id),
    title       VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL,
    type        VARCHAR(20)  NOT NULL, -- QUIZ, SQL, OPEN_TEXT, FILE
    level       SMALLINT     NOT NULL CHECK (level BETWEEN 1 AND 5),
    rubric_json TEXT,                  -- criteria for AI / objective checks
    answer_key  TEXT,                  -- for objective checks
    max_score   INTEGER      NOT NULL DEFAULT 100
);

CREATE TABLE challenge_submissions (
    id           BIGSERIAL PRIMARY KEY,
    challenge_id BIGINT      NOT NULL REFERENCES challenges (id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    answer       TEXT        NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE assessments (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT      NOT NULL UNIQUE REFERENCES challenge_submissions (id) ON DELETE CASCADE,
    score         INTEGER     NOT NULL,
    passed        BOOLEAN     NOT NULL,
    feedback      TEXT,
    assessed_by   VARCHAR(20) NOT NULL, -- AUTO, AI, MENTOR
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============ Progress / Applications ============
CREATE TABLE progress_events (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(50) NOT NULL, -- ROADMAP_ITEM_DONE, CHALLENGE_PASSED, SKILL_LEVEL_CHANGED, ...
    payload_json TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_progress_events_user ON progress_events (user_id, created_at);

-- Covers both "saved_jobs" and "job_applications" from spec via status SAVED.
CREATE TABLE job_applications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id      BIGINT      NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL DEFAULT 'SAVED', -- SAVED, APPLIED, INTERVIEW, OFFER, REJECTED
    notes       TEXT,
    applied_at  TIMESTAMPTZ,
    remind_at   TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, job_id)
);
