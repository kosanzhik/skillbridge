"use client";

import { useQuery } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { LevelDots, Loading, PageHeader, SourceBadge, Stat } from "@/components/app/common";
import { GoalAwareError } from "@/components/app/no-goal";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api } from "@/lib/api";
import { GAP_LABEL } from "@/lib/labels";
import type { GapStatus, SkillGapResponse } from "@/lib/types";

const STATUS_CLS: Record<GapStatus, string> = {
  STRONG: "bg-emerald-100 text-emerald-900",
  NEEDS_IMPROVEMENT: "bg-amber-100 text-amber-900",
  BIG_GAP: "bg-red-100 text-red-900",
};

export default function SkillGapPage() {
  const q = useQuery({ queryKey: ["skill-gap"], queryFn: () => api<SkillGapResponse>("/me/skill-gap") });
  if (q.isLoading) return <Loading />;
  if (q.error) return <GoalAwareError error={q.error} />;
  const d = q.data!;
  const chart = d.skills.slice(0, 12).map((s) => ({ name: s.skill.name, "Ваш уровень": s.currentLevel, "Требуется": s.requiredLevel }));

  return (
    <>
      <PageHeader title={`Skill Gap: ${d.profession.title}`}
        description={d.requirementsSource === "MARKET"
          ? `Важность навыков рассчитана по ${d.jobsAnalyzed} реальным вакансиям.`
          : `Пока мало вакансий для анализа рынка (${d.jobsAnalyzed}) — используются базовые требования профессии.`} />

      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <Stat label="Job Readiness Score" value={`${Math.round(d.readiness.score)}%`} hint="с учётом CV и самооценки" />
        <Stat label="Подтверждено практикой" value={`${Math.round(d.readiness.provenScore)}%`} hint="только навыки, проверенные заданиями" />
        <Stat label="Источник требований" value={d.requirementsSource === "MARKET" ? "Рынок" : "Базовые"} hint={`вакансий: ${d.jobsAnalyzed}`} />
      </div>
      <p className="mb-6 text-xs text-muted-foreground">{d.readiness.explanation}</p>

      <Card className="mb-6">
        <CardHeader><CardTitle className="text-base">Текущий и требуемый уровень (топ по приоритету)</CardTitle></CardHeader>
        <CardContent className="h-80">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chart} margin={{ left: -20 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="name" tick={{ fontSize: 11 }} interval={0} angle={-30} textAnchor="end" height={70} />
              <YAxis domain={[0, 5]} ticks={[0, 1, 2, 3, 4, 5]} />
              <Tooltip />
              <Legend />
              <Bar dataKey="Ваш уровень" fill="oklch(0.6 0.17 250)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
              <Bar dataKey="Требуется" fill="oklch(0.75 0.03 250)" radius={[4, 4, 0, 0]} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Навык</TableHead><TableHead>Текущий</TableHead><TableHead>Требуемый</TableHead>
                <TableHead>Источник</TableHead><TableHead>Спрос</TableHead><TableHead>Статус</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {d.skills.map((s) => (
                <TableRow key={s.skill.id}>
                  <TableCell className="font-medium">{s.skill.name}</TableCell>
                  <TableCell><span className="flex items-center gap-2"><LevelDots level={s.currentLevel} target={s.requiredLevel} />{s.currentLevel}/5</span></TableCell>
                  <TableCell>{s.requiredLevel}/5</TableCell>
                  <TableCell><SourceBadge source={s.currentSource} proven={s.proven} /></TableCell>
                  <TableCell className="text-muted-foreground">
                    {s.demandShare != null ? `${Math.round(s.demandShare * 100)}% вакансий` : `важность ${s.importance}/5`}
                  </TableCell>
                  <TableCell><Badge className={STATUS_CLS[s.status]}>{GAP_LABEL[s.status]}</Badge></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      <details className="mt-4 text-xs text-muted-foreground"><summary className="cursor-pointer">Методика</summary><p className="mt-2">{d.methodology}</p></details>
    </>
  );
}
