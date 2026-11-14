-- MVP professions (spec §3) and skill catalog.
-- profession_skills are baseline expectations only; they are refined
-- from real vacancy data by the Job Integration / Skill Analysis modules.

INSERT INTO professions (slug, title, description, hh_query) VALUES
('data-analyst',      'Data Analyst',      'Сбор, очистка и анализ данных, построение отчётов и дашбордов.', 'Data Analyst OR аналитик данных'),
('financial-analyst', 'Financial Analyst', 'Финансовый анализ, моделирование, бюджетирование и оценка компаний.', 'Financial Analyst OR финансовый аналитик'),
('business-analyst',  'Business Analyst',  'Выявление и описание бизнес-требований, моделирование процессов.', 'Business Analyst OR бизнес-аналитик'),
('software-engineer', 'Software Engineer', 'Проектирование, разработка и сопровождение программного обеспечения.', 'Software Engineer OR разработчик');

INSERT INTO skills (slug, name, category, aliases) VALUES
-- tools / languages
('sql',                'SQL',                         'LANGUAGE', 'postgresql,mysql,t-sql,pl/sql,ms sql,oracle sql'),
('python',             'Python',                      'LANGUAGE', 'python3'),
('pandas',             'Pandas',                      'TOOL',     'numpy'),
('r',                  'R',                           'LANGUAGE', 'r language,rstudio'),
('java',               'Java',                        'LANGUAGE', 'java se,java ee'),
('javascript',         'JavaScript',                  'LANGUAGE', 'js,ecmascript'),
('typescript',         'TypeScript',                  'LANGUAGE', 'ts'),
('excel',              'Excel',                       'TOOL',     'ms excel,microsoft excel,google sheets,vba,сводные таблицы'),
('power-bi',           'Power BI',                    'TOOL',     'powerbi,dax,power query'),
('tableau',            'Tableau',                     'TOOL',     ''),
('git',                'Git',                         'TOOL',     'github,gitlab'),
('docker',             'Docker',                      'TOOL',     'containers'),
('spring',             'Spring',                      'TOOL',     'spring boot,spring framework'),
('react',              'React',                       'TOOL',     'react.js,reactjs'),
('jira',               'Jira',                        'TOOL',     'confluence,atlassian'),
('bpmn',               'BPMN',                        'METHOD',   'bpmn 2.0,моделирование процессов'),
('uml',                'UML',                         'METHOD',   'диаграммы uml'),
('one-c',              '1C',                          'TOOL',     '1с,1c:предприятие'),
-- domain / methods
('statistics',         'Statistics',                  'DOMAIN',   'статистика,a/b testing,a/b тесты,hypothesis testing'),
('data-visualization', 'Data Visualization',          'METHOD',   'визуализация данных,дашборды,dashboards'),
('data-cleaning',      'Data Cleaning',               'METHOD',   'очистка данных,data wrangling,etl'),
('machine-learning',   'Machine Learning',            'DOMAIN',   'ml,машинное обучение,scikit-learn'),
('accounting',         'Accounting',                  'DOMAIN',   'бухгалтерский учет,бухучет'),
('financial-reporting','Financial Reporting',         'DOMAIN',   'ifrs,мсфо,gaap,финансовая отчетность'),
('financial-modeling', 'Financial Modeling',          'DOMAIN',   'финансовое моделирование,dcf,financial model'),
('financial-analysis', 'Financial Statement Analysis','DOMAIN',   'финансовый анализ,анализ отчетности'),
('valuation',          'Company Valuation',           'DOMAIN',   'оценка компаний,оценка бизнеса'),
('budgeting',          'Budgeting & Forecasting',     'DOMAIN',   'бюджетирование,прогнозирование,fp&a'),
('requirements',       'Requirements Engineering',    'METHOD',   'сбор требований,управление требованиями,user stories,use cases,тз,техническое задание'),
('process-modeling',   'Business Process Analysis',   'METHOD',   'анализ бизнес-процессов,as-is,to-be'),
('agile',              'Agile / Scrum',               'METHOD',   'scrum,kanban,agile'),
('rest-api',           'REST API',                    'METHOD',   'rest,api,openapi,swagger'),
('algorithms',         'Algorithms & Data Structures','DOMAIN',   'алгоритмы,структуры данных'),
('oop',                'OOP',                         'METHOD',   'ооп,solid,design patterns,паттерны проектирования'),
('testing',            'Software Testing',            'METHOD',   'unit tests,junit,автотесты,тестирование'),
('communication',      'Communication',               'SOFT',     'коммуникабельность,коммуникативные навыки,presentation'),
('english',            'English',                     'SOFT',     'английский язык,english b2,upper-intermediate');

-- helper: (profession slug, skill slug, required_level, importance)
INSERT INTO profession_skills (profession_id, skill_id, required_level, importance)
SELECT p.id, s.id, v.req, v.imp
FROM (VALUES
    ('data-analyst','sql',4,5), ('data-analyst','excel',4,4), ('data-analyst','python',3,4),
    ('data-analyst','pandas',3,3), ('data-analyst','statistics',3,4), ('data-analyst','power-bi',3,3),
    ('data-analyst','tableau',3,2), ('data-analyst','data-visualization',3,4), ('data-analyst','data-cleaning',3,3),
    ('data-analyst','communication',3,3), ('data-analyst','english',3,2),

    ('financial-analyst','excel',4,5), ('financial-analyst','financial-modeling',4,5),
    ('financial-analyst','financial-analysis',4,5), ('financial-analyst','accounting',3,4),
    ('financial-analyst','financial-reporting',3,4), ('financial-analyst','valuation',3,3),
    ('financial-analyst','budgeting',3,3), ('financial-analyst','sql',3,3), ('financial-analyst','power-bi',3,3),
    ('financial-analyst','english',3,3), ('financial-analyst','one-c',2,2),

    ('business-analyst','requirements',4,5), ('business-analyst','process-modeling',4,5),
    ('business-analyst','bpmn',3,4), ('business-analyst','uml',3,3), ('business-analyst','sql',3,3),
    ('business-analyst','jira',3,3), ('business-analyst','agile',3,3), ('business-analyst','excel',3,3),
    ('business-analyst','communication',4,4), ('business-analyst','english',3,2),

    ('software-engineer','algorithms',3,4), ('software-engineer','oop',4,4), ('software-engineer','git',3,4),
    ('software-engineer','sql',3,4), ('software-engineer','rest-api',3,4), ('software-engineer','testing',3,3),
    ('software-engineer','java',3,3), ('software-engineer','python',3,3), ('software-engineer','javascript',3,3),
    ('software-engineer','docker',2,3), ('software-engineer','agile',2,2), ('software-engineer','english',3,3)
) AS v(prof, skill, req, imp)
JOIN professions p ON p.slug = v.prof
JOIN skills s ON s.slug = v.skill;
