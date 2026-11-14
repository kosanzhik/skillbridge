"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Circle, CircleDot, ExternalLink, RefreshCw } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import { Empty, Loading, PageHeader } from "@/components/app/common";
import { GoalAwareError } from "@/components/app/no-goal";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { api, ApiError, errorMessage } from "@/lib/api";
import { ITEM_STATUS_LABEL } from "@/lib/labels";
import type { ItemStatus, Roadmap, RoadmapItem } from "@/lib/types";

const ICON = { TODO: Circle, IN_PROGRESS: CircleDot, DONE: CheckCircle2 };

function ItemCard({ item, onStatus }: { item: RoadmapItem; onStatus: (s: ItemStatus) => void }) {
  const Icon = ICON[item.status];
  return (
    <Card className={item.status === "DONE" ? "opacity-75" : undefined}>
      <CardHeader>
        <div className="flex flex-wrap items-start gap-3">
          <Icon className={`mt-0.5 size-5 shrink-0 ${item.status === "DONE" ? "text-emerald-600" : item.status === "IN_PROGRESS" ? "text-amber-600" : "text-muted-foreground"}`} />
          <div className="min-w-0 flex-1 basis-56">
            <CardTitle className="text-base">{item.position}. {item.title}</CardTitle>
            {item.goal && <p className="mt-1 text-sm text-muted-foreground">{item.goal}</p>}
          </div>
          <Badge variant="outline">{ITEM_STATUS_LABEL[item.status]}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        {item.rationale && <p className="rounded-lg bg-muted/50 p-3"><span className="font-medium">Почему: </span>{item.rationale}</p>}
        {item.topics.length > 0 && (
          <div><div className="mb-1 font-medium">Темы</div>
            <ul className="list-inside list-disc text-muted-foreground">{item.topics.map((t) => <li key={t}>{t}</li>)}</ul></div>
        )}
        {item.resources.length > 0 && (
          <div><div className="mb-1 font-medium">Материалы</div>
            <ul className="space-y-1">
              {item.resources.map((r) => (
                <li key={r.id}>
                  <a href={r.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 underline">
                    {r.title} <ExternalLink className="size-3" />
                  </a>
                  <span className="text-muted-foreground"> · {r.provider} · {r.type.toLowerCase()}{r.free && " · бесплатно"}</span>
                </li>
              ))}
            </ul></div>
        )}
        {item.challenges.length > 0 && (
          <div><div className="mb-1 font-medium">Практика</div>
            <div className="flex flex-wrap gap-2">
              {item.challenges.map((c) => (
                <Link key={c.id} href={`/challenges/${c.id}`} className="rounded-md border px-2 py-1 hover:bg-muted">
                  {c.title} <span className="text-muted-foreground">· ур. {c.level}</span>
                </Link>
              ))}
            </div></div>
        )}
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-muted-foreground">
          {item.estimatedHours != null && <span>≈ {item.estimatedHours} ч (оценка)</span>}
          {item.completionCriteria && <span>Критерий: {item.completionCriteria}</span>}
        </div>
        <div className="flex gap-2">
          {item.status !== "IN_PROGRESS" && item.status !== "DONE" && <Button size="sm" variant="outline" onClick={() => onStatus("IN_PROGRESS")}>Начать</Button>}
          {item.status !== "DONE" && <Button size="sm" onClick={() => onStatus("DONE")}>Отметить выполненным</Button>}
          {item.status === "DONE" && <Button size="sm" variant="ghost" onClick={() => onStatus("TODO")}>Вернуть в план</Button>}
        </div>
      </CardContent>
    </Card>
  );
}

export default function RoadmapPage() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["roadmap"], queryFn: () => api<Roadmap>("/me/roadmap") });
  const invalidate = () => ["roadmap", "progress"].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
  const generate = useMutation({
    mutationFn: () => api<Roadmap>("/me/roadmap", { method: "POST" }),
    onSuccess: (r) => { qc.setQueryData(["roadmap"], r); invalidate(); toast.success("Roadmap сформирован"); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const update = useMutation({
    mutationFn: ({ id, status }: { id: number; status: ItemStatus }) =>
      api<Roadmap>(`/me/roadmap/items/${id}`, { method: "PATCH", body: { status } }),
    onSuccess: (r) => { qc.setQueryData(["roadmap"], r); qc.invalidateQueries({ queryKey: ["progress"] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (q.isLoading) return <Loading />;
  if (q.error) {
    if (q.error instanceof ApiError && q.error.code === "ROADMAP_NOT_FOUND") {
      return (
        <>
          <PageHeader title="Roadmap" />
          <Empty title="Плана пока нет">
            План строится из вашего Skill Gap: приоритетные пробелы, правильный порядок изучения и проверенные материалы.
            <div className="mt-4"><Button onClick={() => generate.mutate()} disabled={generate.isPending}>Сформировать Roadmap</Button></div>
          </Empty>
        </>
      );
    }
    return <GoalAwareError error={q.error} />;
  }
  const r = q.data!;
  return (
    <>
      <PageHeader title={`Roadmap: ${r.profession.title}`} description={r.note}
        actions={<Button variant="outline" onClick={() => confirm("Пересобрать план по текущему Skill Gap? Текущий план будет заархивирован.") && generate.mutate()}
          disabled={generate.isPending}><RefreshCw /> Пересобрать</Button>} />
      <div className="mb-6 rounded-xl border p-4">
        <div className="mb-2 flex justify-between text-sm"><span>Прогресс</span><span className="tabular-nums">{r.progress.done} / {r.progress.total}</span></div>
        <Progress value={r.progress.percent} />
      </div>
      <div className="space-y-4">
        {r.items.map((i) => <ItemCard key={i.id} item={i} onStatus={(status) => update.mutate({ id: i.id, status })} />)}
      </div>
    </>
  );
}
