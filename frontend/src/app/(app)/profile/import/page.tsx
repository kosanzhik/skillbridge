"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FileText, Plus, Trash2, Upload } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { Empty, ErrorState, LevelDots, Loading, PageHeader, SourceBadge } from "@/components/app/common";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { api, apiBlobUrl, errorMessage } from "@/lib/api";
import type { ParsedResume, Resume, Skill, UserSkill } from "@/lib/types";

const selectCls = "h-8 rounded-lg border bg-background px-2 text-sm";

function Review({ resume, onConfirmed }: { resume: Resume; onConfirmed: () => void }) {
  const [data, setData] = useState<ParsedResume>(resume.parsed!);
  const catalog = useQuery({ queryKey: ["skills"], queryFn: () => api<Skill[]>("/skills") });

  const confirm = useMutation({
    mutationFn: () => api<Resume>(`/resumes/${resume.id}/confirm`, { body: data }),
    onSuccess: (r) => {
      toast.success(r.unmatchedSkills.length
        ? `Сохранено. Не сохранены навыки вне каталога: ${r.unmatchedSkills.join(", ")}`
        : "Данные профиля сохранены");
      onConfirmed();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const set = <K extends keyof ParsedResume>(k: K, v: ParsedResume[K]) => setData({ ...data, [k]: v });

  return (
    <div className="space-y-6">
      <Alert>
        <FileText />
        <AlertTitle>Проверьте извлечённые данные</AlertTitle>
        <AlertDescription>
          {resume.parser === "OPENAI"
            ? "Данные извлечены с помощью AI — это рекомендации, проверьте и исправьте их."
            : "AI-разбор недоступен: найдены только навыки из каталога по ключевым словам. Добавьте опыт и образование вручную."}
          {resume.parseError && <div className="mt-1">{resume.parseError}</div>}
          <div className="mt-1">Уровень навыка из CV — оценка, а не подтверждённый результат.</div>
        </AlertDescription>
      </Alert>

      <Card>
        <CardHeader><CardTitle className="text-base">Личные данные</CardTitle></CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2"><Label>Имя</Label>
            <Input value={data.fullName ?? ""} onChange={(e) => set("fullName", e.target.value)} /></div>
          <div className="space-y-2"><Label>Город</Label>
            <Input value={data.location ?? ""} onChange={(e) => set("location", e.target.value)} /></div>
          <div className="space-y-2"><Label>Должность / заголовок</Label>
            <Input value={data.headline ?? ""} onChange={(e) => set("headline", e.target.value)} /></div>
          <div className="space-y-2"><Label>Языки (через запятую)</Label>
            <Input value={data.languages.join(", ")}
              onChange={(e) => set("languages", e.target.value.split(",").map((s) => s.trim()).filter(Boolean))} /></div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Опыт работы</CardTitle>
          <CardAction><Button size="sm" variant="outline" onClick={() => set("experiences", [...data.experiences,
            { company: "", title: "", startDate: "", endDate: "", description: "" }])}><Plus /> Добавить</Button></CardAction>
        </CardHeader>
        <CardContent className="space-y-4">
          {data.experiences.length === 0 && <p className="text-sm text-muted-foreground">Не указано.</p>}
          {data.experiences.map((x, i) => {
            const upd = (patch: Partial<typeof x>) =>
              set("experiences", data.experiences.map((e, j) => (j === i ? { ...e, ...patch } : e)));
            return (
              <div key={i} className="grid gap-2 rounded-lg border p-3 sm:grid-cols-2">
                <Input placeholder="Должность *" value={x.title} onChange={(e) => upd({ title: e.target.value })} />
                <Input placeholder="Компания" value={x.company ?? ""} onChange={(e) => upd({ company: e.target.value })} />
                <Input placeholder="Начало (ГГГГ-ММ)" value={x.startDate ?? ""} onChange={(e) => upd({ startDate: e.target.value })} />
                <Input placeholder="Окончание (пусто = по наст. время)" value={x.endDate ?? ""} onChange={(e) => upd({ endDate: e.target.value })} />
                <Textarea className="sm:col-span-2" placeholder="Описание" value={x.description ?? ""}
                  onChange={(e) => upd({ description: e.target.value })} />
                <Button size="sm" variant="ghost" className="justify-self-start text-destructive"
                  onClick={() => set("experiences", data.experiences.filter((_, j) => j !== i))}><Trash2 /> Удалить</Button>
              </div>
            );
          })}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Образование</CardTitle>
          <CardAction><Button size="sm" variant="outline" onClick={() => set("educations", [...data.educations,
            { institution: "", degree: "", field: "", startYear: null, endYear: null }])}><Plus /> Добавить</Button></CardAction>
        </CardHeader>
        <CardContent className="space-y-4">
          {data.educations.length === 0 && <p className="text-sm text-muted-foreground">Не указано.</p>}
          {data.educations.map((x, i) => {
            const upd = (patch: Partial<typeof x>) =>
              set("educations", data.educations.map((e, j) => (j === i ? { ...e, ...patch } : e)));
            const year = (v: string) => (v ? Number(v) : null);
            return (
              <div key={i} className="grid gap-2 rounded-lg border p-3 sm:grid-cols-2">
                <Input placeholder="Учебное заведение *" value={x.institution} onChange={(e) => upd({ institution: e.target.value })} />
                <Input placeholder="Степень" value={x.degree ?? ""} onChange={(e) => upd({ degree: e.target.value })} />
                <Input placeholder="Специальность" value={x.field ?? ""} onChange={(e) => upd({ field: e.target.value })} />
                <div className="flex gap-2">
                  <Input type="number" placeholder="Начало" value={x.startYear ?? ""} onChange={(e) => upd({ startYear: year(e.target.value) })} />
                  <Input type="number" placeholder="Окончание" value={x.endYear ?? ""} onChange={(e) => upd({ endYear: year(e.target.value) })} />
                </div>
                <Button size="sm" variant="ghost" className="justify-self-start text-destructive"
                  onClick={() => set("educations", data.educations.filter((_, j) => j !== i))}><Trash2 /> Удалить</Button>
              </div>
            );
          })}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Навыки</CardTitle>
          <CardDescription>Навыки вне каталога не сохраняются — сопоставьте их с навыком каталога или удалите.</CardDescription>
          <CardAction><Button size="sm" variant="outline" onClick={() => set("skills", [...data.skills,
            { name: "", level: 2, evidence: "Указано пользователем", skillId: null, skillName: null }])}><Plus /> Добавить</Button></CardAction>
        </CardHeader>
        <CardContent className="space-y-2">
          {data.skills.map((s, i) => {
            const upd = (patch: Partial<typeof s>) =>
              set("skills", data.skills.map((e, j) => (j === i ? { ...e, ...patch } : e)));
            return (
              <div key={i} className="flex flex-wrap items-center gap-2 rounded-lg border p-2">
                <select className={selectCls + " min-w-48 flex-1"} value={s.skillId ?? ""}
                  onChange={(e) => {
                    const sk = catalog.data?.find((c) => c.id === Number(e.target.value));
                    upd({ skillId: sk?.id ?? null, skillName: sk?.name ?? null, name: s.name || sk?.name || "" });
                  }}>
                  <option value="">{s.name ? `«${s.name}» — нет в каталоге` : "Выберите навык"}</option>
                  {catalog.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
                <select className={selectCls} value={s.level ?? 2} onChange={(e) => upd({ level: Number(e.target.value) })}
                  aria-label="Уровень">
                  {[1, 2, 3, 4, 5].map((l) => <option key={l} value={l}>{l}/5</option>)}
                </select>
                {s.skillId == null && <Badge variant="outline">не сохранится</Badge>}
                {s.evidence && <span className="w-full text-xs text-muted-foreground sm:w-auto sm:flex-1 truncate" title={s.evidence}>{s.evidence}</span>}
                <Button size="icon-sm" variant="ghost" aria-label="Удалить навык"
                  onClick={() => set("skills", data.skills.filter((_, j) => j !== i))}><Trash2 /></Button>
              </div>
            );
          })}
        </CardContent>
      </Card>

      <Button size="lg" disabled={confirm.isPending} onClick={() => confirm.mutate()}>
        Подтвердить и сохранить в профиль
      </Button>
    </div>
  );
}

function CurrentProfile() {
  const skills = useQuery({ queryKey: ["my-skills"], queryFn: () => api<UserSkill[]>("/me/skills") });
  if (!skills.data?.length) return null;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Навыки в профиле</CardTitle>
        <CardDescription>Подтверждённые заданиями навыки важнее заявленных в CV.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        {skills.data.map((s) => (
          <div key={`${s.skill.id}-${s.source}`} className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <span className="font-medium">{s.skill.name}</span>
            <span className="flex items-center gap-3">
              <SourceBadge source={s.source} proven={s.proven} />
              <LevelDots level={s.level} />
            </span>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

export default function ImportPage() {
  const qc = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const resumes = useQuery({ queryKey: ["resumes"], queryFn: () => api<Resume[]>("/resumes") });
  const latest = resumes.data?.[0];

  const upload = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append("file", file);
      return api<Resume>("/resumes", { form });
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["resumes"] }),
    onError: (e) => toast.error(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (id: number) => api(`/resumes/${id}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["resumes"] }),
    onError: (e) => toast.error(errorMessage(e)),
  });
  const openPdf = async (id: number) => {
    try {
      window.open(await apiBlobUrl(`/resumes/${id}/file`), "_blank", "noopener");
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };
  const onConfirmed = () => {
    for (const k of ["resumes", "my-skills", "progress", "skill-gap", "matches", "me"]) qc.invalidateQueries({ queryKey: [k] });
  };

  return (
    <>
      <PageHeader title="CV и профиль" description="Загрузите резюме в PDF. Мы извлечём опыт, образование и навыки — вы проверите и подтвердите."
        actions={
          <>
            <input ref={fileRef} type="file" accept="application/pdf,.pdf" className="hidden"
              onChange={(e) => { const f = e.target.files?.[0]; if (f) upload.mutate(f); e.target.value = ""; }} />
            <Button onClick={() => fileRef.current?.click()} disabled={upload.isPending}>
              <Upload /> {upload.isPending ? "Анализируем…" : "Загрузить CV (PDF)"}
            </Button>
          </>
        } />

      {resumes.isLoading && <Loading />}
      {resumes.error && <ErrorState error={resumes.error} />}
      {resumes.data && resumes.data.length === 0 && (
        <Empty title="CV ещё не загружено">PDF до 10 МБ с текстовым слоем (не скан). Для демо подойдёт docs/demo/demo-cv.pdf.</Empty>
      )}

      {latest && (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-3 rounded-xl border p-4 text-sm">
            <FileText className="size-4" />
            <span className="font-medium">{latest.originalFilename}</span>
            <Badge variant={latest.status === "CONFIRMED" ? "default" : "secondary"}>
              {latest.status === "CONFIRMED" ? "Подтверждено" : latest.status === "PARSED" ? "Ожидает проверки" : latest.status}
            </Badge>
            <span className="ml-auto flex gap-1">
              <Button size="sm" variant="ghost" onClick={() => openPdf(latest.id)}>Открыть PDF</Button>
              <Button size="sm" variant="ghost" className="text-destructive"
                onClick={() => confirm("Удалить файл CV? Подтверждённые данные профиля останутся.") && remove.mutate(latest.id)}>
                Удалить файл
              </Button>
            </span>
          </div>
          {latest.parsed && (latest.status === "PARSED" || latest.status === "CONFIRMED") && (
            <Review key={`${latest.id}-${latest.status}-${latest.confirmedAt}`} resume={latest} onConfirmed={onConfirmed} />
          )}
          {latest.status === "FAILED" && <ErrorState error={new Error(latest.parseError ?? "Не удалось разобрать CV")} />}
        </div>
      )}

      <div className="mt-8"><CurrentProfile /></div>
    </>
  );
}
