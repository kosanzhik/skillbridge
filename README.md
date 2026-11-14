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
