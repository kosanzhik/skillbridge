"use client";

import Link from "next/link";
import { useState } from "react";
import { useAuthActions } from "@/components/app/auth";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { errorMessage } from "@/lib/api";

export default function RegisterPage() {
  const { register } = useAuthActions();
  const [form, setForm] = useState({ fullName: "", email: "", password: "" });
  const [consent, setConsent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await register(form.email, form.password, form.fullName, consent);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Регистрация</CardTitle>
          <CardDescription>Создайте аккаунт, чтобы сохранить профиль и прогресс.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={submit}>
            <div className="space-y-2">
              <Label htmlFor="name">Имя (необязательно)</Label>
              <Input id="name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Пароль (от 8 символов)</Label>
              <Input id="password" type="password" minLength={8} required autoComplete="new-password"
                value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
            </div>
            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" className="mt-1" checked={consent} onChange={(e) => setConsent(e.target.checked)} required />
              <span>
                Я согласен(на) на обработку персональных данных, включая загруженные документы. Аккаунт и документы
                можно удалить в любой момент в настройках.
              </span>
            </label>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button type="submit" className="w-full" disabled={busy || !consent}>Создать аккаунт</Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Уже есть аккаунт? <Link href="/login" className="underline">Войти</Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
