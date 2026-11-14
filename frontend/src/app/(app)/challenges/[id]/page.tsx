"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, XCircle } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { ErrorState, Loading, PageHeader } from "@/components/app/common";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { api, errorMessage } from "@/lib/api";
import type { Challenge, SubmissionResult } from "@/lib/types";
import { cn } from "@/lib/utils";

type QuestionResult = { id: string; correct: boolean; given: number | null; expected: number };

function ResultPanel({ r, challenge }: { r: SubmissionResult; challenge: Challenge }) {
  if (r.status === "PENDING") {
    return <Alert><AlertTitle>Ответ сохранён</AlertTitle><AlertDescription>{r.feedback}</AlertDescription></Alert>;
  }
  const passed = r.status === "PASSED";
  const d = (r.details ?? {}) as { columns?: string[]; preview?: string[][]; rowCount?: number; expectedRowCount?: number;
    criteria?: { name: string; points: number; maxPoints: number; comment: string }[] };
  return (
    <Card className={passed ? "border-emerald-300" : "border-red-300"}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          {passed ? <CheckCircle2 className="text-emerald-600" /> : <XCircle className="text-red-600" />}
          {passed ? "Задание пройдено" : "Пока не пройдено"} · {r.score}% (нужно {r.passScore}%)
          {r.assessedBy === "AI" && <Badge variant="outline">оценка AI — рекомендация</Badge>}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        {r.feedback && <p className="whitespace-pre-line">{r.feedback}</p>}
        {r.skillUpdate && (
          <p className="rounded-lg bg-emerald-50 p-3 text-emerald-900">
            Навык {r.skillUpdate.skill}: подтверждённый уровень {r.skillUpdate.previousAssessedLevel ?? 0} → {r.skillUpdate.newAssessedLevel}.
            Skill Gap и подбор вакансий обновлены.
          </p>
        )}
        {r.roadmapItemCompletedId && <p>Модуль Roadmap закрыт автоматически. <Link href="/roadmap" className="underline">Открыть Roadmap</Link></p>}
        {d.criteria && (
          <ul className="space-y-1">{d.criteria.map((c) => <li key={c.name}><b>{c.points}/{c.maxPoints}</b> — {c.comment}</li>)}</ul>
        )}
        {d.columns && d.preview && (
          <div>
            <p className="mb-1 text-muted-foreground">Ваш результат: {d.rowCount} строк (ожидается {d.expectedRowCount})</p>
            <div className="max-h-64 overflow-auto rounded-lg border">
              <Table>
                <TableHeader><TableRow>{d.columns.map((c) => <TableHead key={c}>{c}</TableHead>)}</TableRow></TableHeader>
                <TableBody>{d.preview.map((row, i) => <TableRow key={i}>{row.map((v, j) => <TableCell key={j}>{v}</TableCell>)}</TableRow>)}</TableBody>
              </Table>
            </div>
          </div>
        )}
        {challenge.type === "QUIZ" && <p className="text-muted-foreground">Правильные ответы отмечены в вопросах ниже.</p>}
      </CardContent>
    </Card>
  );
}

export default function ChallengePage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["challenge", id], queryFn: () => api<Challenge>(`/challenges/${id}`) });
  const [answers, setAnswers] = useState<Record<string, number>>({});
  const [sql, setSql] = useState("SELECT *\nFROM customers\nLIMIT 10");
  const [text, setText] = useState("");
  const [result, setResult] = useState<SubmissionResult | null>(null);

  const submit = useMutation({
    mutationFn: () => api<SubmissionResult>(`/challenges/${id}/submissions`, {
      body: q.data!.type === "QUIZ" ? { answers } : q.data!.type === "SQL" ? { sql } : { text },
    }),
    onSuccess: (r) => {
      setResult(r);
      for (const k of ["progress", "roadmap", "skill-gap", "matches", "my-skills", "submissions"]) qc.invalidateQueries({ queryKey: [k] });
      window.scrollTo({ top: 0, behavior: "smooth" });
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (q.isLoading) return <Loading />;
  if (q.error) return <ErrorState error={q.error} />;
  const c = q.data!;
  const qResults = new Map(((result?.details as { questions?: QuestionResult[] } | null)?.questions ?? []).map((x) => [x.id, x]));

  return (
    <>
      <PageHeader title={c.title} description={`${c.skill.name} · уровень ${c.level} · проходной балл ${c.passScore}%`} />
      {result && <div className="mb-6"><ResultPanel r={result} challenge={c} /></div>}

      <Card className="mb-6">
        <CardContent><p className="whitespace-pre-line text-sm">{c.description}</p></CardContent>
      </Card>

      {c.type === "QUIZ" && (
        <div className="space-y-4">
          {c.questions.map((question, qi) => {
            const res = qResults.get(question.id);
            return (
              <Card key={question.id}>
                <CardHeader><CardTitle className="text-base">{qi + 1}. {question.text}</CardTitle></CardHeader>
                <CardContent className="space-y-2">
                  {question.options.map((o, oi) => (
                    <label key={oi} className={cn("flex cursor-pointer items-center gap-2 rounded-lg border p-2 text-sm",
                      res && oi === res.expected && "border-emerald-500 bg-emerald-50",
                      res && !res.correct && oi === res.given && "border-red-500 bg-red-50")}>
                      <input type="radio" name={question.id} checked={answers[question.id] === oi}
                        onChange={() => setAnswers({ ...answers, [question.id]: oi })} />
                      {o}
                    </label>
                  ))}
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {c.type === "SQL" && (
        <Card>
          <CardHeader><CardTitle className="text-base">Ваш запрос (PostgreSQL, только SELECT)</CardTitle></CardHeader>
          <CardContent>
            <Textarea value={sql} onChange={(e) => setSql(e.target.value)} rows={10} spellCheck={false}
              className="font-mono text-sm" onKeyDown={(e) => { if ((e.metaKey || e.ctrlKey) && e.key === "Enter") submit.mutate(); }} />
            <p className="mt-2 text-xs text-muted-foreground">Ctrl/⌘ + Enter — отправить. Учебные данные синтетические.</p>
          </CardContent>
        </Card>
      )}

      {c.type === "OPEN_TEXT" && (
        <Card>
          <CardHeader><CardTitle className="text-base">Ваш ответ</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <Textarea value={text} onChange={(e) => setText(e.target.value)} rows={12} />
            <div className="text-xs text-muted-foreground">
              Критерии оценки:
              <ul className="mt-1 list-inside list-disc">{c.criteria.map((cr) => <li key={cr.name}>{cr.description} — до {cr.maxPoints} б.</li>)}</ul>
            </div>
          </CardContent>
        </Card>
      )}

      <Button className="mt-6" size="lg" onClick={() => submit.mutate()}
        disabled={submit.isPending || (c.type === "QUIZ" && Object.keys(answers).length < c.questions.length)}>
        {submit.isPending ? "Проверяем…" : "Отправить на проверку"}
      </Button>
    </>
  );
}
