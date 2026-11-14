"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { ErrorState, Loading, PageHeader } from "@/components/app/common";
import { useMe } from "@/components/app/use-me";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api, errorMessage, tokenStore } from "@/lib/api";
import { FORMAT_LABEL, LEVEL_LABEL } from "@/lib/labels";
import type { Me } from "@/lib/types";

const selectCls = "h-8 w-full rounded-lg border bg-background px-2.5 text-sm";

export default function SettingsPage() {
  const me = useMe();
  if (me.isLoading) return <Loading />;
  if (me.error) return <ErrorState error={me.error} />;
  return <Settings key={me.data!.id} me={me.data!} />;
}

function Settings({ me }: { me: Me }) {
  const qc = useQueryClient();
  const router = useRouter();
  const p = me.profile;
  const [f, setF] = useState({ fullName: p.fullName ?? "", location: p.location ?? "", headline: p.headline ?? "",
    languages: p.languages.join(", "), desiredLevel: p.desiredLevel ?? "", workFormat: p.workFormat ?? "" });
  const [confirmText, setConfirmText] = useState("");

  const save = useMutation({
    mutationFn: () => api<Me>("/me/profile", { method: "PUT", body: {
      fullName: f.fullName, location: f.location, headline: f.headline,
      languages: f.languages.split(",").map((s) => s.trim()).filter(Boolean),
      desiredLevel: f.desiredLevel || null, workFormat: f.workFormat || null, completeOnboarding: true } }),
    onSuccess: (m) => { qc.setQueryData(["me"], m); toast.success("Профиль сохранён"); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const del = useMutation({
    mutationFn: () => api("/me", { method: "DELETE" }),
    onSuccess: () => { tokenStore.clear(); qc.clear(); router.push("/"); toast.success("Аккаунт и все данные удалены"); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <>
      <PageHeader title="Настройки" description={me.email} />
      <Card className="mb-6">
        <CardHeader><CardTitle className="text-base">Профиль</CardTitle></CardHeader>
        <CardContent>
          <form className="grid gap-4 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
            {([["fullName", "Имя"], ["location", "Город"], ["headline", "Должность / заголовок"], ["languages", "Языки (через запятую)"]] as const).map(([k, l]) => (
              <div key={k} className="space-y-2"><Label htmlFor={k}>{l}</Label>
                <Input id={k} value={f[k]} onChange={(e) => setF({ ...f, [k]: e.target.value })} /></div>
            ))}
            <div className="space-y-2"><Label htmlFor="lvl">Желаемый уровень</Label>
              <select id="lvl" className={selectCls} value={f.desiredLevel} onChange={(e) => setF({ ...f, desiredLevel: e.target.value })}>
                <option value="">—</option>{Object.entries(LEVEL_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select></div>
            <div className="space-y-2"><Label htmlFor="fmt">Формат работы</Label>
              <select id="fmt" className={selectCls} value={f.workFormat} onChange={(e) => setF({ ...f, workFormat: e.target.value })}>
                <option value="">—</option>{Object.entries(FORMAT_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select></div>
            <div className="sm:col-span-2"><Button type="submit" disabled={save.isPending}>Сохранить</Button></div>
          </form>
        </CardContent>
      </Card>

      <Card className="border-destructive/40">
        <CardHeader>
          <CardTitle className="text-base text-destructive">Удаление аккаунта</CardTitle>
          <CardDescription>
            Безвозвратно удаляет аккаунт, профиль, загруженные CV, навыки, цели, Roadmap и результаты заданий.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <Label htmlFor="confirm">Введите УДАЛИТЬ для подтверждения</Label>
          <Input id="confirm" value={confirmText} onChange={(e) => setConfirmText(e.target.value)} className="max-w-xs" />
          <Button variant="destructive" disabled={confirmText !== "УДАЛИТЬ" || del.isPending} onClick={() => del.mutate()}>
            Удалить аккаунт и данные
          </Button>
        </CardContent>
      </Card>
    </>
  );
}
