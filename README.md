# SkillBridge — AI Career Development Platform

Платформа, которая помогает перейти от текущего набора навыков к желаемой профессии:
анализ CV → реальные вакансии → Skill Gap → персональный Roadmap → практические задания → прогресс.

> **Real Job Market → Skill Gap Analysis → Personalized Roadmap → Learning → Practical Assessment → Updated Skills → Better Job Matches**

Полная спецификация: [docs/SPECIFICATION.md](docs/SPECIFICATION.md)

## Структура

```
skillbridge/
├── backend/            # Java + Spring Boot (модульный монолит, REST, JWT)
├── frontend/           # Next.js + TypeScript + Tailwind + shadcn/ui
├── docs/               # Спецификация и документация
└── docker-compose.yml  # PostgreSQL + pgvector
```

## Быстрый старт

```bash
cp .env.example .env
docker compose up -d postgres

# backend
cd backend && ./mvnw spring-boot:run

# frontend
cd frontend && npm install && npm run dev
```

## MVP (хакатон)

1. Регистрация / демо-профиль
2. Загрузка CV (PDF)
3. Извлечение опыта и навыков с помощью AI
4. Выбор одной целевой профессии
5. Реальные вакансии из одного источника (hh.kz API)
6. Сопоставление навыков с требованиями вакансий
7. Skill Gap
8. Персональный Roadmap
9. Учебные материалы с проверяемыми ссылками
10. Отслеживание выполнения учебных задач

## API (backend, base URL `http://localhost:8080/api`)

Аутентификация — `Authorization: Bearer <JWT>` (HS256, срок жизни 24 ч).

| Метод | Путь | Описание |
|---|---|---|
| POST | `/auth/register` | Регистрация `{email, password, fullName?, consent: true}` |
| POST | `/auth/login` | Вход `{email, password}` |
| POST | `/auth/demo` | Новый демо-аккаунт с заполненным профилем |
| GET | `/me` | Текущий пользователь и профиль |
| PUT | `/me/profile` | Обновление профиля / завершение онбординга |
| DELETE | `/me` | Удаление аккаунта и всех данных пользователя |

Ошибки возвращаются как RFC 9457 `ProblemDetail` с полем `code` (`EMAIL_TAKEN`, `BAD_CREDENTIALS`, `VALIDATION_ERROR`, …).

## База данных

Миграции Flyway: `backend/src/main/resources/db/migration`
- `V1__init_schema.sql` — все сущности из спецификации (§9)
- `V2__seed_catalog.sql` — 4 MVP-профессии, каталог навыков и базовые требования

## Тесты

```bash
cd backend && ./mvnw test   # нужен запущенный Docker (Testcontainers + pgvector)
```
