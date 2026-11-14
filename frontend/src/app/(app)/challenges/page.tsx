"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Empty, Loading, PageHeader } from "@/components/app/common";
import { SkillPicker } from "@/components/app/skill-picker";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";
import { CHALLENGE_TYPE_LABEL as TYPE_LABEL } from "@/lib/labels";
import type { Challenge, SubmissionResult } from "@/lib/types";

export default function ChallengesPage() {
  const [skill, setSkill] = useState("");
  const list = useQuery({
    queryKey: ["challenges", skill], enabled: !!skill,
    queryFn: () => api<Challenge[]>(`/challenges?skill=${encodeURIComponent(skill)}`),
  });
  const history = useQuery({ queryKey: ["submissions"], queryFn: () => api<SubmissionResult[]>("/me/submissions") });

  return (
    <>
      <PageHeader title="Практические задания"
        description="Тесты и SQL-задачи проверяются автоматически. Пройденное задание подтверждает уровень навыка."
        actions={<SkillPicker value={skill} onChange={setSkill} />} />
      {list.isLoading && <Loading rows={2} />}
      {list.data && list.data.length === 0 && <Empty title="Для этого навыка заданий пока нет" />}
      <div className="grid gap-3 md:grid-cols-2">
        {list.data?.map((c) => (
          <Link key={c.id} href={`/challenges/${c.id}`}>
            <Card className="h-full transition-colors hover:bg-muted/40">
              <CardHeader>
                <CardTitle className="text-base">{c.title}</CardTitle>
                <CardDescription className="line-clamp-2">{c.description}</CardDescription>
              </CardHeader>
              <CardContent className="flex gap-2">
                <Badge variant="secondary">{TYPE_LABEL[c.type]}</Badge>
                <Badge variant="outline">уровень {c.level}</Badge>
                <Badge variant="outline">проход {c.passScore}%</Badge>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>

      <h2 className="mb-3 mt-10 text-lg font-semibold">История</h2>
      {history.data?.length ? (
        <div className="space-y-2">
          {history.data.map((h) => (
            <div key={h.submissionId} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3 text-sm">
              <Link href={`/challenges/${h.challengeId}`} className="font-medium hover:underline">{h.challengeTitle}</Link>
              <span className="flex items-center gap-2">
                <span className="tabular-nums">{h.score}%</span>
                <Badge variant={h.status === "PASSED" ? "default" : "secondary"}>{h.status === "PASSED" ? "Пройдено" : "Не пройдено"}</Badge>
                {h.assessedBy === "AI" && <Badge variant="outline">оценка AI</Badge>}
              </span>
            </div>
          ))}
        </div>
      ) : <p className="text-sm text-muted-foreground">Вы ещё не выполняли задания.</p>}
    </>
  );
}
