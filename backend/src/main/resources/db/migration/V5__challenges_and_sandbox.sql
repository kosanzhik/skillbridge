-- Practical challenges (spec §4 step 8) and an isolated SQL sandbox.

ALTER TABLE challenges ADD COLUMN pass_score INTEGER NOT NULL DEFAULT 70;
ALTER TABLE assessments ADD COLUMN details_json TEXT;
ALTER TABLE roadmap_items ADD COLUMN target_level SMALLINT;

-- ============ SQL sandbox ============
-- User SQL runs ONLY as this unprivileged role: no superuser, read-only by default, short timeout,
-- SELECT on the training schema only.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${sandbox_user}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION CONNECTION LIMIT 10',
                       '${sandbox_user}', '${sandbox_password}');
    ELSE
        EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION',
                       '${sandbox_user}', '${sandbox_password}');
    END IF;
    EXECUTE format('ALTER ROLE %I SET default_transaction_read_only = on', '${sandbox_user}');
    EXECUTE format('ALTER ROLE %I SET statement_timeout = %L', '${sandbox_user}', '3s');
    EXECUTE format('ALTER ROLE %I SET search_path = sandbox', '${sandbox_user}');
    EXECUTE format('REVOKE TEMPORARY ON DATABASE %I FROM PUBLIC', current_database());
END
$$;

CREATE SCHEMA sandbox;
COMMENT ON SCHEMA sandbox IS 'Synthetic training dataset for SQL challenges (not real people or companies)';

CREATE TABLE sandbox.customers (
    id          INTEGER PRIMARY KEY,
    name        TEXT NOT NULL,
    city        TEXT NOT NULL,
    signup_date DATE NOT NULL
);
CREATE TABLE sandbox.products (
    id       INTEGER PRIMARY KEY,
    name     TEXT    NOT NULL,
    category TEXT    NOT NULL,
    price    INTEGER NOT NULL
);
CREATE TABLE sandbox.orders (
    id          INTEGER PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES sandbox.customers (id),
    order_date  DATE    NOT NULL,
    status      TEXT    NOT NULL -- completed | cancelled
);
CREATE TABLE sandbox.order_items (
    order_id   INTEGER NOT NULL REFERENCES sandbox.orders (id),
    product_id INTEGER NOT NULL REFERENCES sandbox.products (id),
    quantity   INTEGER NOT NULL,
    unit_price INTEGER NOT NULL,
    PRIMARY KEY (order_id, product_id)
);

INSERT INTO sandbox.customers VALUES
(1, 'Aigerim', 'Almaty',    '2025-01-15'),
(2, 'Daniyar', 'Astana',    '2025-02-03'),
(3, 'Madina',  'Almaty',    '2025-02-20'),
(4, 'Arman',   'Shymkent',  '2025-03-11'),
(5, 'Dana',    'Astana',    '2025-04-02'),
(6, 'Yerlan',  'Almaty',    '2025-05-19'),
(7, 'Saule',   'Karaganda', '2025-06-07'),
(8, 'Timur',   'Astana',    '2025-07-25');

INSERT INTO sandbox.products VALUES
(1, 'Laptop',       'Electronics', 450000),
(2, 'Headphones',   'Electronics',  35000),
(3, 'Office Chair', 'Furniture',    80000),
(4, 'Desk',         'Furniture',   120000),
(5, 'Notebook Set', 'Stationery',    5000),
(6, 'Monitor',      'Electronics', 150000);

INSERT INTO sandbox.orders VALUES
(1,  1, '2025-03-01', 'completed'),
(2,  2, '2025-03-05', 'completed'),
(3,  1, '2025-03-20', 'completed'),
(4,  3, '2025-04-02', 'cancelled'),
(5,  4, '2025-04-15', 'completed'),
(6,  5, '2025-05-01', 'completed'),
(7,  2, '2025-05-12', 'completed'),
(8,  6, '2025-06-03', 'completed'),
(9,  1, '2025-06-18', 'cancelled'),
(10, 7, '2025-07-01', 'completed'),
(11, 3, '2025-07-09', 'completed'),
(12, 5, '2025-08-14', 'completed');

INSERT INTO sandbox.order_items VALUES
(1, 1, 1, 450000), (1, 5, 2, 5000),
(2, 2, 1, 35000),
(3, 6, 2, 150000),
(4, 3, 1, 80000),
(5, 4, 1, 120000), (5, 3, 1, 80000),
(6, 2, 2, 35000),
(7, 1, 1, 450000),
(8, 5, 5, 5000),
(9, 6, 1, 150000),
(10, 4, 1, 120000),
(11, 2, 1, 35000), (11, 5, 1, 5000),
(12, 6, 1, 150000), (12, 2, 1, 35000);

DO $$
BEGIN
    EXECUTE format('GRANT USAGE ON SCHEMA sandbox TO %I', '${sandbox_user}');
    EXECUTE format('GRANT SELECT ON ALL TABLES IN SCHEMA sandbox TO %I', '${sandbox_user}');
END
$$;

-- ============ Challenges ============
-- QUIZ: rubric_json = {"questions":[{"id","text","options"}]}, answer_key = {"<id>": <option index>}
-- SQL:  answer_key = reference query, rubric_json = {"ordered": bool}
-- OPEN_TEXT: rubric_json = {"criteria":[{"name","description","maxPoints"}]} — graded by AI against the rubric

INSERT INTO challenges (skill_id, title, description, type, level, rubric_json, answer_key, pass_score)
SELECT s.id, v.title, v.description, v.type, v.lvl, v.rubric, v.answer_key, v.pass
FROM (VALUES
-- ---------- SQL ----------
('sql', 'SQL: клиенты из Алматы', E'Учебная база (схема sandbox): customers(id, name, city, signup_date), products(id, name, category, price), orders(id, customer_id, order_date, status), order_items(order_id, product_id, quantity, unit_price).\n\nВыведите имена (name) клиентов из города Almaty в порядке даты регистрации — от ранних к поздним.',
 'SQL', 2, '{"ordered": true}',
 'SELECT name FROM sandbox.customers WHERE city = ''Almaty'' ORDER BY signup_date', 100),
('sql', 'SQL: выручка по категориям', E'Учебная база (схема sandbox): customers(id, name, city, signup_date), products(id, name, category, price), orders(id, customer_id, order_date, status), order_items(order_id, product_id, quantity, unit_price).\n\nПосчитайте выручку (quantity × unit_price) по категориям товаров только для завершённых заказов (status = ''completed''). Выведите category и revenue, отсортируйте по revenue по убыванию.',
 'SQL', 3, '{"ordered": true}',
 'SELECT p.category, SUM(oi.quantity * oi.unit_price) AS revenue FROM sandbox.order_items oi JOIN sandbox.orders o ON o.id = oi.order_id JOIN sandbox.products p ON p.id = oi.product_id WHERE o.status = ''completed'' GROUP BY p.category ORDER BY revenue DESC', 100),
('sql', 'SQL: все клиенты и их выручка', E'Учебная база (схема sandbox): customers(id, name, city, signup_date), products(id, name, category, price), orders(id, customer_id, order_date, status), order_items(order_id, product_id, quantity, unit_price).\n\nДля КАЖДОГО клиента выведите name, число завершённых заказов (completed_orders) и выручку по ним (revenue). Клиенты без завершённых заказов должны попасть в результат с нулями. Сортировка: revenue по убыванию, затем name по возрастанию.',
 'SQL', 4, '{"ordered": true}',
 'SELECT c.name, COUNT(DISTINCT o.id) AS completed_orders, COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS revenue FROM sandbox.customers c LEFT JOIN sandbox.orders o ON o.customer_id = c.id AND o.status = ''completed'' LEFT JOIN sandbox.order_items oi ON oi.order_id = o.id GROUP BY c.id, c.name ORDER BY revenue DESC, c.name', 100),

-- ---------- Quizzes ----------
('excel', 'Excel: формулы и сводные таблицы', 'Тест из 5 вопросов по формулам, ссылкам и инструментам анализа в Excel.', 'QUIZ', 3,
 '{"questions":[
   {"id":"q1","text":"Какая формула вернёт значение из столбца C для строки, где в столбце A точно найдено значение из E1?","options":["=VLOOKUP(E1;A:C;3;TRUE)","=VLOOKUP(E1;A:C;3;FALSE)","=HLOOKUP(E1;A:C;3;FALSE)","=MATCH(E1;A:C;0)"]},
   {"id":"q2","text":"Какая ссылка фиксирует и столбец, и строку при копировании формулы?","options":["A1","A$1","$A1","$A$1"]},
   {"id":"q3","text":"Как быстрее всего получить сумму продаж по регионам и месяцам из таблицы на 10 000 строк?","options":["Сводная таблица","Условное форматирование","Проверка данных","Защита листа"]},
   {"id":"q4","text":"Что вернёт =SUMIFS(C:C;A:A;\"Almaty\";B:B;\">100\")?","options":["Количество строк, где A = Almaty","Сумму столбца B для Almaty","Сумму значений C, где A = Almaty и B > 100","Ошибку: условий не может быть два"]},
   {"id":"q5","text":"Для чего прежде всего предназначен Power Query?","options":["Импорт и преобразование данных","Рисование диаграмм","Защита книги паролем","Запись макросов VBA"]}]}',
 '{"q1":1,"q2":3,"q3":0,"q4":2,"q5":0}', 70),
('statistics', 'Статистика: основы анализа данных', 'Тест из 5 вопросов по описательной статистике и проверке гипотез.', 'QUIZ', 3,
 '{"questions":[
   {"id":"q1","text":"Медиана набора 3, 7, 1, 9, 5 равна:","options":["3","5","5,5","7"]},
   {"id":"q2","text":"p-value = 0,03 при уровне значимости 0,05 означает:","options":["Нулевая гипотеза верна с вероятностью 3%","Отвергаем нулевую гипотезу","Альтернатива верна с вероятностью 97%","Результат незначим"]},
   {"id":"q3","text":"Какая мера центральной тенденции наиболее устойчива к выбросам?","options":["Среднее арифметическое","Размах","Медиана","Стандартное отклонение"]},
   {"id":"q4","text":"В A/B-тесте конверсия A = 10%, B = 12% на небольшой выборке. Что нужно сделать до вывода?","options":["Сразу выбрать B","Проверить статистическую значимость различия","Сравнить медианы конверсий","Увеличить конверсию A"]},
   {"id":"q5","text":"Коэффициент корреляции 0,9 между X и Y означает:","options":["X вызывает Y","Связи нет","Сильную отрицательную связь","Сильную положительную линейную связь"]}]}',
 '{"q1":1,"q2":1,"q3":2,"q4":1,"q5":3}', 70),
('pandas', 'Pandas: работа с таблицами', 'Тест из 4 вопросов по базовым операциям pandas.', 'QUIZ', 3,
 '{"questions":[
   {"id":"q1","text":"Что вернёт df.groupby(\"city\")[\"revenue\"].sum()?","options":["Сумму revenue по каждому городу (Series)","Общую сумму revenue (число)","Количество строк по городам","Исходный DataFrame, отсортированный по city"]},
   {"id":"q2","text":"Как отобрать строки, где revenue > 1000 и city равен \"Almaty\"?","options":["df[df.revenue > 1000 and df.city == \"Almaty\"]","df[(df.revenue > 1000) & (df.city == \"Almaty\")]","df.where(revenue > 1000, city = \"Almaty\")","df.filter(revenue > 1000).filter(city = \"Almaty\")"]},
   {"id":"q3","text":"pd.merge(a, b, on=\"id\", how=\"left\") вернёт:","options":["Только совпадающие строки","Все строки b","Все строки a и совпадения из b","Декартово произведение"]},
   {"id":"q4","text":"Как заменить все пропуски в DataFrame нулями?","options":["df.dropna()","df.isna(0)","df.replace(None)","df.fillna(0)"]}]}',
 '{"q1":0,"q2":1,"q3":2,"q4":3}', 70),
('power-bi', 'Power BI: модель данных и DAX', 'Тест из 4 вопросов по Power BI.', 'QUIZ', 2,
 '{"questions":[
   {"id":"q1","text":"На каком языке пишутся меры в Power BI?","options":["M","DAX","VBA","Python"]},
   {"id":"q2","text":"Какой язык используется в Power Query для преобразования данных?","options":["M","DAX","SQL","R"]},
   {"id":"q3","text":"Какая связь обычно между таблицей фактов продаж и справочником товаров?","options":["Один к одному","Многие ко многим","Многие к одному (продажи → товар)","Связь не нужна"]},
   {"id":"q4","text":"Чем мера отличается от вычисляемого столбца?","options":["Мера считается для каждой строки при загрузке","Мера вычисляется в контексте фильтров визуала","Меры нельзя использовать в визуалах","Ничем"]}]}',
 '{"q1":1,"q2":0,"q3":2,"q4":1}', 70),
('accounting', 'Бухгалтерский учёт: основы', 'Тест из 4 вопросов по принципам учёта.', 'QUIZ', 2,
 '{"questions":[
   {"id":"q1","text":"Основное балансовое уравнение:","options":["Активы = Обязательства + Капитал","Активы = Выручка − Расходы","Капитал = Активы + Обязательства","Обязательства = Активы + Капитал"]},
   {"id":"q2","text":"Начисленная, но ещё не выплаченная зарплата отражается в балансе как:","options":["Актив","Капитал","Обязательство","Выручка"]},
   {"id":"q3","text":"Амортизация основных средств:","options":["Увеличивает денежные средства","Уменьшает прибыль, но не требует оттока денег в периоде","Не влияет на прибыль","Является обязательством"]},
   {"id":"q4","text":"По методу начисления выручка признаётся, когда:","options":["Поступили деньги","Подписан договор","Выполнена обязанность перед покупателем (товар передан, услуга оказана)","Выставлен счёт"]}]}',
 '{"q1":0,"q2":2,"q3":1,"q4":2}', 70),
('financial-analysis', 'Финансовый анализ: коэффициенты', 'Тест из 5 вопросов по анализу отчётности.', 'QUIZ', 3,
 '{"questions":[
   {"id":"q1","text":"Коэффициент текущей ликвидности =","options":["Денежные средства / Активы","Оборотные активы / Краткосрочные обязательства","Чистая прибыль / Выручка","Долг / Капитал"]},
   {"id":"q2","text":"ROE рассчитывается как:","options":["Чистая прибыль / Собственный капитал","Чистая прибыль / Активы","EBIT / Выручка","Выручка / Капитал"]},
   {"id":"q3","text":"Выручка 1000, себестоимость 600, операционные расходы 250. Операционная маржа:","options":["40%","25%","15%","60%"]},
   {"id":"q4","text":"Debt/Equity вырос с 0,5 до 1,5. Это означает:","options":["Компания стала менее закредитованной","Выросла долговая нагрузка","Выросла ликвидность","Выросла рентабельность"]},
   {"id":"q5","text":"Прибыль положительная, а операционный денежный поток отрицательный. Частая причина:","options":["Рост дебиторской задолженности и запасов","Рост амортизации","Снижение налогов","Выплата дивидендов"]}]}',
 '{"q1":1,"q2":0,"q3":2,"q4":1,"q5":0}', 70),
('financial-modeling', 'Финансовое моделирование: структура модели', 'Тест из 4 вопросов о построении финансовой модели.', 'QUIZ', 3,
 '{"questions":[
   {"id":"q1","text":"Как чистая прибыль связывает три формы отчётности?","options":["Никак не связывает","Переносится в нераспределённую прибыль баланса и начинает ДДС косвенным методом","Идёт только в ДДС","Равна изменению денежных средств"]},
   {"id":"q2","text":"Хорошая практика размещения допущений:","options":["Вписывать числа прямо в формулы","Отдельный блок допущений, на который ссылаются формулы","Хранить допущения в комментариях","Повторять допущения на каждом листе"]},
   {"id":"q3","text":"Проверка корректности баланса в модели:","options":["Выручка > 0","Активы − (Обязательства + Капитал) = 0","Денежные средства > 0","Прибыль = ДДС"]},
   {"id":"q4","text":"Анализ чувствительности показывает:","options":["Как меняется результат при изменении ключевых допущений","Историческую динамику выручки","Структуру акционеров","Ошибки в формулах"]}]}',
 '{"q1":1,"q2":1,"q3":1,"q4":0}', 70),
('valuation', 'Оценка компаний: DCF и мультипликаторы', 'Тест из 5 вопросов по оценке бизнеса.', 'QUIZ', 3,
 '{"questions":[
   {"id":"q1","text":"Стоимость компании по методу DCF — это:","options":["Сумма активов по балансу","Дисконтированные будущие свободные денежные потоки","Рыночная капитализация","Выручка × мультипликатор"]},
   {"id":"q2","text":"Если WACC растёт при прочих равных, стоимость по DCF:","options":["Растёт","Не меняется","Снижается","Становится отрицательной"]},
   {"id":"q3","text":"Денежный поток для всех инвесторов (FCFF) дисконтируют по:","options":["Стоимости собственного капитала","Ставке по депозиту","Темпу роста g","WACC"]},
   {"id":"q4","text":"Терминальная стоимость по модели Гордона:","options":["FCF(n+1) / (WACC − g)","FCF(n) × WACC","FCF(n) / g","(WACC − g) / FCF(n+1)"]},
   {"id":"q5","text":"С чем корректно сравнивать EV/EBITDA компании?","options":["С её P/E","С EV/EBITDA сопоставимых компаний отрасли","С инфляцией","С WACC"]}]}',
 '{"q1":1,"q2":2,"q3":3,"q4":0,"q5":1}', 70),
('requirements', 'Требования: user stories и критерии приёмки', 'Тест из 4 вопросов по работе с требованиями.', 'QUIZ', 3,
 '{"questions":[
   {"id":"q1","text":"Классический формат user story:","options":["Система должна…","Как <роль>, я хочу <действие>, чтобы <ценность>","Если… то…","Задача: … Срок: …"]},
   {"id":"q2","text":"Зачем нужны критерии приёмки?","options":["Чтобы проверяемо определить, что история выполнена","Чтобы оценить стоимость","Чтобы назначить исполнителя","Они не нужны в Agile"]},
   {"id":"q3","text":"«Страница должна загружаться не дольше 2 секунд» — это:","options":["Функциональное требование","Бизнес-правило","Нефункциональное требование","User story"]},
   {"id":"q4","text":"Что означает MoSCoW?","options":["Must, Should, Could, Won’t","Model, Scope, Cost, Workflow","Метод оценки сроков","Нотация процессов"]}]}',
 '{"q1":1,"q2":0,"q3":2,"q4":0}', 70),
('bpmn', 'BPMN: элементы нотации', 'Тест из 4 вопросов по BPMN 2.0.', 'QUIZ', 2,
 '{"questions":[
   {"id":"q1","text":"Что обозначает ромб в BPMN?","options":["Задачу","Шлюз (развилку)","Событие","Подпроцесс"]},
   {"id":"q2","text":"Эксклюзивный шлюз (XOR) означает:","options":["Выполняются все ветки","Выбирается ровно одна ветка","Ветки выполняются параллельно","Процесс завершается"]},
   {"id":"q3","text":"Пулы и дорожки (pools/lanes) показывают:","options":["Участников и роли процесса","Порядок задач","Данные процесса","Время выполнения"]},
   {"id":"q4","text":"С чего начинается процесс в BPMN?","options":["С задачи","Со шлюза","Со стартового события","С конечного события"]}]}',
 '{"q1":1,"q2":1,"q3":0,"q4":2}', 70),
('git', 'Git: повседневная работа', 'Тест из 4 вопросов по Git.', 'QUIZ', 2,
 '{"questions":[
   {"id":"q1","text":"Как создать ветку feature и сразу переключиться на неё?","options":["git branch -d feature","git merge feature","git switch -c feature","git reset feature"]},
   {"id":"q2","text":"git pull по умолчанию — это:","options":["git fetch + слияние (или rebase) удалённой ветки","Только git fetch","git push в обратную сторону","Клонирование репозитория"]},
   {"id":"q3","text":"Как отменить незакоммиченные изменения в файле app.py?","options":["git revert app.py","git restore app.py","git commit --amend","git rm app.py"]},
   {"id":"q4","text":"Конфликт слияния возникает, когда:","options":["В ветках разные коммиты","Одни и те же строки изменены в обеих ветках","Ветка слишком старая","Нет доступа к remote"]}]}',
 '{"q1":2,"q2":0,"q3":1,"q4":1}', 70),
('algorithms', 'Алгоритмы: сложность и выбор структуры', 'Тест из 4 вопросов по алгоритмам и структурам данных.', 'QUIZ', 3,
 '{"questions":[
   {"id":"q1","text":"Сложность бинарного поиска в отсортированном массиве:","options":["O(n)","O(log n)","O(n log n)","O(1)"]},
   {"id":"q2","text":"Средняя сложность поиска по ключу в хеш-таблице:","options":["O(1)","O(log n)","O(n)","O(n²)"]},
   {"id":"q3","text":"Кратчайший путь по числу рёбер в невзвешенном графе находит:","options":["DFS","Сортировка","BFS","Бинарный поиск"]},
   {"id":"q4","text":"Сложность сортировки слиянием в худшем случае:","options":["O(n²)","O(n)","O(log n)","O(n log n)"]}]}',
 '{"q1":1,"q2":0,"q3":2,"q4":3}', 70),

-- ---------- Open text (AI rubric) ----------
('requirements', 'Требования: user stories для загрузки CV', E'Опишите 3 user stories для функции «Загрузка CV» в сервисе карьерного развития. Для каждой укажите критерии приёмки (не менее 2), включая хотя бы один негативный сценарий (например, неверный формат файла).',
 'OPEN_TEXT', 3,
 '{"criteria":[
   {"name":"format","description":"Истории в формате «Как <роль>, я хочу <действие>, чтобы <ценность>», роль и ценность понятны","maxPoints":3},
   {"name":"acceptance","description":"У каждой истории ≥ 2 проверяемых критерия приёмки (конкретные условия и ожидаемый результат)","maxPoints":4},
   {"name":"edge_cases","description":"Учтены негативные сценарии: неверный формат, слишком большой файл, пустой/сканированный PDF и т.п.","maxPoints":3}]}',
 NULL, 70)
) AS v(skill, title, description, type, lvl, rubric, answer_key, pass)
JOIN skills s ON s.slug = v.skill;
