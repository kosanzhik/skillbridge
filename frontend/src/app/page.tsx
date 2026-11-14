"use client";

import { ArrowRight, Briefcase, ClipboardCheck, FileUp, Map, Target, TrendingUp } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { useAuthActions } from "@/components/app/auth";
import { Button, buttonVariants } from "@/components/ui/button";
import { errorMessage } from "@/lib/api";

const STEPS = [
  { icon: FileUp, title: "Загрузите CV", text: "Извлекаем опыт, образование и навыки — вы проверяете и исправляете результат." },
  { icon: Target, title: "Выберите профессию", text: "Data Analyst, Financial Analyst, Business Analyst или Software Engineer." },
  { icon: Briefcase, title: "Реальные вакансии", text: "hh.kz и канал команды. Процент совпадения с объяснением, без выдуманных зарплат." },
  { icon: TrendingUp, title: "Skill Gap", text: "Что уже умеете, чего не хватает и что важнее всего для работодателей." },
  { icon: Map, title: "Персональный Roadmap", text: "Модули в правильном порядке, с проверенными ссылками на материалы." },
  { icon: ClipboardCheck, title: "Практика", text: "Тесты и SQL-задачи подтверждают навыки — профиль и вакансии обновляются." },
];

export default function Landing() {
  const { demo } = useAuthActions();
  const [busy, setBusy] = useState(false);
  const startDemo = async () => {
    setBusy(true);
    try {
      await demo();
    } catch (e) {
      toast.error(errorMessage(e));
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col">
      <header className="mx-auto flex w-full max-w-6xl items-center justify-between px-4 py-5 sm:px-6">
        <span className="text-lg font-semibold">SkillBridge</span>
        <div className="flex gap-2">
          <Link href="/login" className={buttonVariants({ variant: "ghost" })}>Войти</Link>
          <Link href="/register" className={buttonVariants()}>Регистрация</Link>
        </div>
      </header>

      <main className="flex-1">
        <section className="mx-auto max-w-6xl px-4 pb-16 pt-12 sm:px-6 sm:pt-20">
          <p className="mb-4 text-sm font-medium text-muted-foreground">AI Career Development Platform</p>
          <h1 className="max-w-3xl text-4xl font-semibold tracking-tight sm:text-5xl">
            От того, что вы умеете сейчас, — к профессии, которую хотите.
          </h1>
          <p className="mt-6 max-w-2xl text-lg text-muted-foreground">
            SkillBridge сравнивает ваш профиль с требованиями реальных вакансий, показывает пробелы в навыках
            и строит план развития с практическими заданиями, которые подтверждают результат.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link href="/register" className={buttonVariants({ size: "lg" })}>
              Начать <ArrowRight />
            </Link>
            <Button size="lg" variant="outline" onClick={startDemo} disabled={busy}>
              {busy ? "Создаём демо…" : "Попробовать демо-профиль"}
            </Button>
          </div>
        </section>

        <section className="border-t bg-muted/30">
          <div className="mx-auto grid max-w-6xl gap-6 px-4 py-14 sm:grid-cols-2 sm:px-6 lg:grid-cols-3">
            {STEPS.map(({ icon: Icon, title, text }, i) => (
              <div key={title} className="rounded-xl border bg-background p-5">
                <div className="mb-3 flex items-center gap-2 text-sm text-muted-foreground">
                  <span className="tabular-nums">{String(i + 1).padStart(2, "0")}</span>
                  <Icon className="size-4" />
                </div>
                <h3 className="font-medium">{title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{text}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-4 py-14 sm:px-6">
          <h2 className="text-xl font-semibold">Честно о том, что мы показываем</h2>
          <ul className="mt-4 grid gap-3 text-sm text-muted-foreground sm:grid-cols-2">
            <li>Вакансии — только реальные, со ссылкой на источник. Нет зарплаты в источнике — пишем «не указано».</li>
            <li>Процент совпадения — оценка соответствия требованиям, а не вероятность трудоустройства.</li>
            <li>Навык из CV и навык, подтверждённый заданием, — это разные вещи, и мы их различаем.</li>
            <li>Вы можете исправить извлечённые данные и в любой момент удалить аккаунт вместе с документами.</li>
          </ul>
        </section>
      </main>

      <footer className="border-t py-6 text-center text-xs text-muted-foreground">
        SkillBridge · From knowing where you are to proving where you can go.
      </footer>
    </div>
  );
}
