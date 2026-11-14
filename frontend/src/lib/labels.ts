import type { DesiredLevel, GapStatus, ItemStatus, JobSource, JobSummary, SkillSource, WorkFormat } from "./types";

export const SOURCE_LABEL: Record<SkillSource, string> = {
  SELF_REPORTED: "Самооценка",
  CV_EXTRACTED: "Из CV",
  ASSESSED: "Подтверждено заданием",
  VERIFIED: "Подтверждено внешним источником",
};

export const GAP_LABEL: Record<GapStatus, string> = {
  STRONG: "Сильная сторона",
  NEEDS_IMPROVEMENT: "Нужно улучшить",
  BIG_GAP: "Большой пробел",
};

export const ITEM_STATUS_LABEL: Record<ItemStatus, string> = {
  TODO: "Не начато",
  IN_PROGRESS: "В процессе",
  DONE: "Выполнено",
};

export const LEVEL_LABEL: Record<DesiredLevel, string> = {
  INTERN: "Стажёр", JUNIOR: "Junior", MIDDLE: "Middle", SENIOR: "Senior", LEAD: "Lead",
};

export const FORMAT_LABEL: Record<WorkFormat, string> = {
  OFFICE: "Офис", HYBRID: "Гибрид", REMOTE: "Удалённо", ANY: "Любой",
};

export const CHALLENGE_TYPE_LABEL = { QUIZ: "Тест", SQL: "SQL-задача", OPEN_TEXT: "Развёрнутый ответ" } as const;

export const NOT_SPECIFIED = "не указано";

/** Name + home page of each source, shown as attribution next to every vacancy. */
export const SOURCE_INFO: Record<JobSource, { name: string; home: string }> = {
  HH: { name: "hh.kz", home: "https://hh.kz" },
  TELEGRAM: { name: "Telegram", home: "https://t.me" },
  REMOTIVE: { name: "Remotive", home: "https://remotive.com" },
  HIMALAYAS: { name: "Himalayas", home: "https://himalayas.app" },
};

const PERIOD_SUFFIX: Record<string, string> = { HOUR: "/час", DAY: "/день", WEEK: "/нед.", MONTH: "/мес", YEAR: "/год" };

/** Never invents a salary: structured range with its period, or the source's own text, or «не указано». */
export function jobSalary(j: JobSummary): string {
  if (j.salaryFrom == null && j.salaryTo == null) return j.salaryText ?? NOT_SPECIFIED;
  const base = formatSalary(j.salaryFrom, j.salaryTo, j.salaryCurrency);
  return j.salaryPeriod ? `${base} ${PERIOD_SUFFIX[j.salaryPeriod] ?? ""}`.trim() : base;
}

export function formatSalary(from: number | null, to: number | null, currency: string | null): string {
  if (from == null && to == null) return NOT_SPECIFIED;
  const f = (n: number) => n.toLocaleString("ru-RU");
  const cur = currency ? ` ${currency === "KZT" ? "₸" : currency}` : "";
  if (from != null && to != null) return `${f(from)} – ${f(to)}${cur}`;
  if (from != null) return `от ${f(from)}${cur}`;
  return `до ${f(to!)}${cur}`;
}

export function formatDate(iso: string | null): string {
  if (!iso) return NOT_SPECIFIED;
  return new Date(iso).toLocaleDateString("ru-RU", { day: "numeric", month: "long", year: "numeric" });
}
