-- Remote job sources (Remotive, Himalayas): salary period / free-text salary, remote flag,
-- and title keywords to classify vacancies into MVP professions.

ALTER TABLE jobs ADD COLUMN salary_period VARCHAR(10);  -- HOUR, DAY, WEEK, MONTH, YEAR (null = not stated)
ALTER TABLE jobs ADD COLUMN salary_text   VARCHAR(150); -- salary exactly as published when it is free text
ALTER TABLE jobs ADD COLUMN remote        BOOLEAN NOT NULL DEFAULT FALSE;

-- hh.kz salaries are monthly by convention
UPDATE jobs SET salary_period = 'MONTH' WHERE source = 'HH' AND (salary_from IS NOT NULL OR salary_to IS NOT NULL);

ALTER TABLE professions ADD COLUMN title_keywords TEXT; -- comma-separated phrases matched in vacancy titles

UPDATE professions SET title_keywords = v.kw FROM (VALUES
('data-analyst',      'data analyst,аналитик данных,bi analyst,bi-аналитик,business intelligence analyst,reporting analyst,product analyst,продуктовый аналитик'),
('financial-analyst', 'financial analyst,finance analyst,финансовый аналитик,fp&a,investment analyst,инвестиционный аналитик,financial planning analyst'),
('business-analyst',  'business analyst,бизнес-аналитик,бизнес аналитик,systems analyst,системный аналитик,business systems analyst,requirements analyst'),
('software-engineer', 'software engineer,software developer,backend developer,backend engineer,back-end developer,back-end engineer,frontend developer,frontend engineer,front-end developer,front-end engineer,full stack,fullstack,full-stack,java developer,python developer,web developer,разработчик,программист')
) AS v(slug, kw)
WHERE professions.slug = v.slug;
