"use client";

import { useQuery } from "@tanstack/react-query";
import { ExternalLink } from "lucide-react";
import Link from "next/link";
import { Empty, Loading, PageHeader } from "@/components/app/common";
import { GoalAwareError } from "@/components/app/no-goal";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { api } from "@/lib/api";
import { formatDate, jobSalary, NOT_SPECIFIED, SOURCE_INFO } from "@/lib/labels";
import type { JobMatch, MatchesResponse, SkillMatch } from "@/lib/types";

function SkillChips({ items, tone }: { items: SkillMatch[]; tone: "ok" | "partial" | "missing" }) {
  if (!items.length) return null;
  const cls = { ok: "bg-emerald-100 text-emerald-900", partial: "bg-amber-100 text-amber-900", missing: "bg-red-100 text-red-900" }[tone];
  return (
    <div className="flex flex-wrap gap-1.5">
      {items.map((s) => (
        <span key={s.skillId} className={`rounded-md px-2 py-0.5 text-xs ${cls}`}
          title={`Требуется ${s.requiredLevel}/5, у вас ${s.userLevel}/5 · ${s.requirement === "REQUIRED" ? "обязательный" : "желательный"}`}>
          {s.skill}{s.requirement === "PREFERRED" && " · желат."}{tone === "partial" && ` ${s.userLevel}/${s.requiredLevel}`}
        </span>
      ))}
    </div>
  );
}

function MatchCard({ m }: { m: JobMatch }) {
  const j = m.job;
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle className="text-base">
              <Link href={`/jobs/${j.id}`} className="hover:underline">{j.title}</Link>
            </CardTitle>
            <p className="mt-1 text-sm text-muted-foreground">
              {j.company ?? NOT_SPECIFIED} · {j.location ?? NOT_SPECIFIED}
            </p>
            {j.remote && <Badge variant="outline" className="mt-2">удалённо · международная вакансия</Badge>}
          </div>
          <div className="text-right">
            <div className="text-2xl font-semibold tabular-nums">{m.score == null ? "—" : `${Math.round(m.score)}%`}</div>
            <div className="text-xs text-muted-foreground">совпадение навыков</div>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        {m.score != null ? <Progress value={m.score} /> : (
          <p className="text-muted-foreground">В описании не удалось определить требования к навыкам — оценка не рассчитана.</p>
        )}
        {m.matched.length > 0 && <div><div className="mb-1 text-xs text-muted-foreground">Уже есть</div><SkillChips items={m.matched} tone="ok" /></div>}
        {m.partial.length > 0 && <div><div className="mb-1 text-xs text-muted-foreground">Нужно подтянуть</div><SkillChips items={m.partial} tone="partial" /></div>}
        {m.missing.length > 0 && <div><div className="mb-1 text-xs text-muted-foreground">Нужно развить</div><SkillChips items={m.missing} tone="missing" /></div>}
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-muted-foreground">
          <span>Зарплата: {jobSalary(j)}</span>
          <span>Опыт: {j.experience ?? NOT_SPECIFIED}</span>
          <span>Опубликовано: {formatDate(j.publishedAt)}</span>
          <a href={j.url} target="_blank" rel="noopener" className="ml-auto inline-flex items-center gap-1 text-foreground underline">
            Открыть на {SOURCE_INFO[j.source].name} <ExternalLink className="size-3" />
          </a>
        </div>
      </CardContent>
    </Card>
  );
}

export default function JobsPage() {
  const q = useQuery({ queryKey: ["matches"], queryFn: () => api<MatchesResponse>("/me/job-matches?limit=50") });
  if (q.isLoading) return <Loading />;
  if (q.error) return <GoalAwareError error={q.error} />;
  const d = q.data!;
  return (
    <>
      <PageHeader title={`Вакансии: ${d.profession.title}`}
        description={`Проанализировано вакансий: ${d.jobsAnalyzed}. ${d.disclaimer}`} />
      <p className="-mt-4 mb-6 text-xs text-muted-foreground">
        Источники: <a className="underline" href="https://hh.kz" target="_blank" rel="noopener">hh.kz</a>,{" "}
        <a className="underline" href="https://remotive.com" target="_blank" rel="noopener">Remotive</a>,{" "}
        <a className="underline" href="https://himalayas.app" target="_blank" rel="noopener">Himalayas</a>.
        Отклик — на сайте источника по ссылке «Открыть на…». У удалённых вакансий проверьте регион: часть открыта только
        для отдельных стран.
      </p>
      <details className="mb-6 rounded-xl border p-4 text-sm">
        <summary className="cursor-pointer font-medium">Как считается процент совпадения</summary>
        <p className="mt-2 text-muted-foreground">{d.methodology}</p>
        <div className="mt-2 flex gap-2"><Badge className="bg-emerald-100 text-emerald-900">уже есть</Badge>
          <Badge className="bg-amber-100 text-amber-900">ниже требуемого</Badge><Badge className="bg-red-100 text-red-900">нет в профиле</Badge></div>
      </details>
      {d.matches.length === 0 ? (
        <Empty title="Вакансий пока нет">
          Вакансии загружаются из hh.kz и открытых площадок удалённой работы (Remotive, Himalayas). Выдуманных
          вакансий мы не показываем: если подходящих сейчас нет, список пуст.
        </Empty>
      ) : (
        <div className="space-y-4">{d.matches.map((m) => <MatchCard key={m.job.id} m={m} />)}</div>
      )}
    </>
  );
}
