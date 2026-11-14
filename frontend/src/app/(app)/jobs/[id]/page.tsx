"use client";

import { useQuery } from "@tanstack/react-query";
import { ExternalLink } from "lucide-react";
import { useParams } from "next/navigation";
import { ErrorState, Loading, PageHeader } from "@/components/app/common";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";
import { formatDate, formatSalary, NOT_SPECIFIED } from "@/lib/labels";
import type { JobDetail } from "@/lib/types";

export default function JobPage() {
  const { id } = useParams<{ id: string }>();
  const q = useQuery({ queryKey: ["job", id], queryFn: () => api<JobDetail>(`/jobs/${id}`) });
  if (q.isLoading) return <Loading />;
  if (q.error) return <ErrorState error={q.error} />;
  const { job: j, description, requirements, employment } = q.data!;
  const rows: [string, string][] = [
    ["Компания", j.company ?? NOT_SPECIFIED], ["Локация", j.location ?? NOT_SPECIFIED],
    ["Зарплата", formatSalary(j.salaryFrom, j.salaryTo, j.salaryCurrency) + (j.salaryGross === true ? " (до вычета налогов)" : j.salaryGross === false ? " (на руки)" : "")],
    ["Опыт", j.experience ?? NOT_SPECIFIED], ["Занятость", employment ?? NOT_SPECIFIED], ["График", j.schedule ?? NOT_SPECIFIED],
    ["Опубликовано", formatDate(j.publishedAt)], ["Источник", j.source === "HH" ? "hh.kz" : "Telegram-канал"],
  ];
  return (
    <>
      <PageHeader title={j.title} actions={
        <a href={j.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-sm underline">
          Открыть оригинал <ExternalLink className="size-3" />
        </a>} />
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardContent className="space-y-2 text-sm">
            {rows.map(([k, v]) => <div key={k}><div className="text-muted-foreground">{k}</div><div>{v}</div></div>)}
          </CardContent>
        </Card>
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader><CardTitle className="text-base">Требования к навыкам</CardTitle></CardHeader>
            <CardContent className="flex flex-wrap gap-2">
              {requirements.length === 0 && <span className="text-sm text-muted-foreground">Не определены.</span>}
              {requirements.map((r) => (
                <Badge key={r.skill.id} variant={r.type === "REQUIRED" ? "default" : "outline"}>
                  {r.skill.name}{r.type === "PREFERRED" && " · желательно"}
                </Badge>
              ))}
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle className="text-base">Описание</CardTitle></CardHeader>
            <CardContent><p className="whitespace-pre-line text-sm">{description ?? NOT_SPECIFIED}</p></CardContent>
          </Card>
        </div>
      </div>
    </>
  );
}
