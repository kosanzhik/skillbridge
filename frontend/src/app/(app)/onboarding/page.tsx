"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { ErrorState, Loading, PageHeader } from "@/components/app/common";
import { useMe } from "@/components/app/use-me";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api, ApiError, errorMessage } from "@/lib/api";
import { FORMAT_LABEL, LEVEL_LABEL } from "@/lib/labels";
import type { DesiredLevel, Me, ProfessionSummary, WorkFormat } from "@/lib/types";
import { cn } from "@/lib/utils";

const selectCls = "h-8 w-full rounded-lg border bg-background px-2.5 text-sm";

export default function OnboardingPage() {
  const me = useMe();
  if (me.isLoading) return <Loading />;
  if (me.error) return <ErrorState error={me.error} />;
  return <Onboarding key={me.data!.id} me={me.data!} />;
}

function Onboarding({ me }: { me: Me }) {
  const router = useRouter();
  const qc = useQueryClient();
  const professions = useQuery({ queryKey: ["professions"], queryFn: () => api<ProfessionSummary[]>("/professions") });
  const p = me.profile;
  const [form, setForm] = useState({
    fullName: p.fullName ?? "", location: p.location ?? "", languages: p.languages.join(", "),
    desiredLevel: p.desiredLevel ?? "", workFormat: p.workFormat ?? "",
  });
  const [profession, setProfession] = useState<string>("");

  const save = useMutation({
    mutationFn: async () => {
      const updated = await api<Me>("/me/profile", {
        method: "PUT",
        body: {
          fullName: form.fullName, location: form.location, headline: p.headline ?? null,
          languages: form.languages.split(",").map((s) => s.trim()).filter(Boolean),
          desiredLevel: form.desiredLevel || null, workFormat: form.workFormat || null, completeOnboarding: true,
        },
      });
      if (profession) {
        await api("/me/career-goals", { body: { professionSlug: profession, primary: true } }).catch((e) => {
          if (!(e instanceof ApiError && e.code === "GOAL_EXISTS")) throw e;
        });
      }
      return updated;
    },
    onSuccess: (updated) => {
      qc.setQueryData(["me"], updated);
      qc.invalidateQueries({ queryKey: ["goals"] });
      router.push("/profile/import");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <>
      <PageHeader title="Знакомство" description="Пара вопросов о вас и ваших целях. Всё можно изменить позже." />
      <form className="grid gap-6 lg:grid-cols-2" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
        <Card>
          <CardHeader>
            <CardTitle>О вас</CardTitle>
            <CardDescription>Имя и город необязательны.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="fn">Имя</Label>
              <Input id="fn" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="loc">Город</Label>
              <Input id="loc" value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="lang">Языки (через запятую)</Label>
              <Input id="lang" placeholder="Казахский, Русский, English B2" value={form.languages}
                onChange={(e) => setForm({ ...form, languages: e.target.value })} />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="lvl">Желаемый уровень</Label>
                <select id="lvl" className={selectCls} value={form.desiredLevel}
                  onChange={(e) => setForm({ ...form, desiredLevel: e.target.value as DesiredLevel })}>
                  <option value="">—</option>
                  {Object.entries(LEVEL_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="fmt">Формат работы</Label>
                <select id="fmt" className={selectCls} value={form.workFormat}
                  onChange={(e) => setForm({ ...form, workFormat: e.target.value as WorkFormat })}>
                  <option value="">—</option>
                  {Object.entries(FORMAT_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Целевая профессия</CardTitle>
            <CardDescription>Для первого анализа выберите одну основную цель.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            {professions.isLoading && <Loading rows={2} />}
            {professions.data?.map((p) => (
              <button type="button" key={p.slug} onClick={() => setProfession(p.slug)}
                className={cn("w-full rounded-lg border p-3 text-left transition-colors hover:bg-muted",
                  profession === p.slug && "border-primary bg-muted")}>
                <div className="font-medium">{p.title}</div>
                <div className="text-sm text-muted-foreground">{p.description}</div>
              </button>
            ))}
          </CardContent>
        </Card>

        <div className="lg:col-span-2">
          <Button type="submit" size="lg" disabled={save.isPending}>Продолжить — загрузить CV</Button>
        </div>
      </form>
    </>
  );
}
