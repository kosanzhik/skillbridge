"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { ErrorState, Loading, PageHeader, Stat } from "@/components/app/common";
import { useMe } from "@/components/app/use-me";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { api } from "@/lib/api";
import { ITEM_STATUS_LABEL } from "@/lib/labels";
import type { ItemStatus, ProgressResponse } from "@/lib/types";

const ACTION_LINK: Record<ProgressResponse["nextAction"]["type"], string> = {
  UPLOAD_CV: "/profile/import",
  CHOOSE_GOAL: "/goals",
  GENERATE_ROADMAP: "/roadmap",
  CONTINUE_ITEM: "/roadmap",
  REVIEW_JOBS: "/jobs",
};

function describe(e: ProgressResponse["recentEvents"][number]): string {
  const p = e.payload as Record<string, string | number | boolean>;
  switch (e.type) {
    case "ROADMAP_GENERATED": return `Сформирован Roadmap (${p.modules} модулей)`;
    case "ROADMAP_ITEM_STATUS_CHANGED":
      return `«${p.title}»: ${ITEM_STATUS_LABEL[p.to as ItemStatus] ?? p.to}${p.auto ? " (автоматически после задания)" : ""}`;
    case "CHALLENGE_SUBMITTED":
      return `Задание «${p.title}»: ${p.status === "PASSED" ? "пройдено" : p.status === "PENDING" ? "на проверке" : "не пройдено"}${p.score != null ? `, ${p.score}%` : ""}`;
    case "SKILL_LEVEL_CHANGED": return `Навык ${p.skill}: подтверждённый уровень ${p.from} → ${p.to}`;
    default: return e.type;
  }
}

export default function DashboardPage() {
  const me = useMe();
  const progress = useQuery({ queryKey: ["progress"], queryFn: () => api<ProgressResponse>("/me/progress") });

  if (progress.isLoading) return <Loading />;
  if (progress.error) return <ErrorState error={progress.error} />;
  const p = progress.data!;
  const name = me.data?.profile.fullName;

  return (
    <>
      <PageHeader title={name ? `Здравствуйте, ${name}` : "Обзор"} description="Ваш прогресс на пути к целевой профессии." />

      <Card className="mb-6 border-primary/30">
        <CardHeader>
          <CardDescription>Следующий шаг</CardDescription>
          <CardTitle className="text-lg">{p.nextAction.title}</CardTitle>
        </CardHeader>
        <CardContent>
          <Link href={ACTION_LINK[p.nextAction.type]} className={buttonVariants()}>
            Перейти <ArrowRight />
          </Link>
        </CardContent>
      </Card>

      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Job Readiness" value={p.readiness ? `${Math.round(p.readiness.score)}%` : "—"}
          hint={p.readiness ? `подтверждено практикой: ${Math.round(p.readiness.provenScore)}%` : "выберите цель"} />
        <Stat label="Roadmap" value={p.roadmap ? `${Math.round(p.roadmap.percent)}%` : "—"}
          hint={p.roadmap ? `${p.roadmap.done} из ${p.roadmap.total} модулей` : "ещё не создан"} />
        <Stat label="Навыков в профиле" value={p.skills.total} hint={`из CV: ${p.skills.cvExtracted}`} />
        <Stat label="Подтверждено заданиями" value={p.skills.proven} />
      </div>

      {p.roadmap && (
        <Card className="mb-6">
          <CardHeader><CardTitle className="text-base">Выполнение плана</CardTitle></CardHeader>
          <CardContent>
            <Progress value={p.roadmap.percent} />
            <p className="mt-2 text-sm text-muted-foreground">
              Выполнено {p.roadmap.done}, в процессе {p.roadmap.inProgress}, всего {p.roadmap.total}.
            </p>
          </CardContent>
        </Card>
      )}

      {p.readiness && (
        <p className="mb-6 text-xs text-muted-foreground">{p.readiness.explanation}</p>
      )}

      <Card>
        <CardHeader><CardTitle className="text-base">Последние события</CardTitle></CardHeader>
        <CardContent>
          {p.recentEvents.length === 0 ? (
            <p className="text-sm text-muted-foreground">Пока ничего — начните с загрузки CV.</p>
          ) : (
            <ul className="space-y-2 text-sm">
              {p.recentEvents.map((e, i) => (
                <li key={i} className="flex justify-between gap-4">
                  <span>{describe(e)}</span>
                  <span className="shrink-0 text-muted-foreground">
                    {new Date(e.createdAt).toLocaleString("ru-RU", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </>
  );
}
