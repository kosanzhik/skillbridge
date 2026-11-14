"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Star, Trash2 } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { ErrorState, LevelDots, Loading, PageHeader } from "@/components/app/common";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { api, errorMessage } from "@/lib/api";
import type { CareerGoal, ProfessionDetail, ProfessionSummary } from "@/lib/types";

function Requirements({ slug }: { slug: string }) {
  const q = useQuery({ queryKey: ["profession", slug], queryFn: () => api<ProfessionDetail>(`/professions/${slug}`) });
  if (q.isLoading) return <p className="text-sm text-muted-foreground">Загрузка…</p>;
  if (!q.data) return null;
  return (
    <div className="mt-3 space-y-1.5">
      <p className="text-xs text-muted-foreground">Базовые требования (уточняются по реальным вакансиям):</p>
      {q.data.requirements.map((r) => (
        <div key={r.skill.id} className="flex items-center justify-between text-sm">
          <span>{r.skill.name}</span>
          <span className="flex items-center gap-3">
            <span className="text-xs text-muted-foreground">важность {r.importance}/5</span>
            <LevelDots level={r.requiredLevel} />
          </span>
        </div>
      ))}
    </div>
  );
}

export default function GoalsPage() {
  const qc = useQueryClient();
  const goals = useQuery({ queryKey: ["goals"], queryFn: () => api<CareerGoal[]>("/me/career-goals") });
  const professions = useQuery({ queryKey: ["professions"], queryFn: () => api<ProfessionSummary[]>("/professions") });
  const [open, setOpen] = useState<string | null>(null);

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["goals"] });
    qc.invalidateQueries({ queryKey: ["progress"] });
    qc.invalidateQueries({ queryKey: ["skill-gap"] });
    qc.invalidateQueries({ queryKey: ["roadmap"] });
    qc.invalidateQueries({ queryKey: ["matches"] });
  };
  const onError = (e: unknown) => toast.error(errorMessage(e));
  const add = useMutation({
    mutationFn: (slug: string) => api("/me/career-goals", { body: { professionSlug: slug } }),
    onSuccess: refresh, onError,
  });
  const primary = useMutation({
    mutationFn: (id: number) => api(`/me/career-goals/${id}/primary`, { method: "PUT" }),
    onSuccess: refresh, onError,
  });
  const remove = useMutation({
    mutationFn: (id: number) => api(`/me/career-goals/${id}`, { method: "DELETE" }),
    onSuccess: refresh, onError,
  });

  if (goals.isLoading || professions.isLoading) return <Loading />;
  if (goals.error) return <ErrorState error={goals.error} />;
  const chosen = new Set(goals.data!.map((g) => g.profession.slug));

  return (
    <>
      <PageHeader title="Карьерные цели"
        description="Можно выбрать до трёх профессий. Анализ, вакансии и Roadmap строятся по основной цели." />

      {goals.data!.length > 0 && (
        <div className="mb-8 space-y-2">
          {goals.data!.map((g) => (
            <div key={g.id} className="flex items-center justify-between rounded-xl border p-4">
              <div className="flex items-center gap-2">
                <span className="font-medium">{g.profession.title}</span>
                {g.primary && <Badge>Основная</Badge>}
              </div>
              <div className="flex gap-1">
                {!g.primary && (
                  <Button variant="ghost" size="sm" onClick={() => primary.mutate(g.id)}><Star /> Сделать основной</Button>
                )}
                <Button variant="ghost" size="icon-sm" aria-label="Удалить цель"
                  onClick={() => confirm(`Удалить цель «${g.profession.title}»? Её Roadmap тоже удалится.`) && remove.mutate(g.id)}>
                  <Trash2 />
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        {professions.data!.map((p) => (
          <Card key={p.slug}>
            <CardHeader>
              <CardTitle>{p.title}</CardTitle>
              <CardDescription>{p.description}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="flex gap-2">
                <Button size="sm" disabled={chosen.has(p.slug) || add.isPending || goals.data!.length >= 3}
                  onClick={() => add.mutate(p.slug)}>
                  {chosen.has(p.slug) ? "Уже в целях" : "Добавить цель"}
                </Button>
                <Button size="sm" variant="outline" onClick={() => setOpen(open === p.slug ? null : p.slug)}>
                  {open === p.slug ? "Скрыть требования" : `Требования (${p.requirementCount})`}
                </Button>
              </div>
              {open === p.slug && <Requirements slug={p.slug} />}
            </CardContent>
          </Card>
        ))}
      </div>
    </>
  );
}
