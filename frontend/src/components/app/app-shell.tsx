"use client";

import {
  BookOpen, Briefcase, ClipboardCheck, FileUp, Gauge, LogOut, Map, Settings, Target, TrendingUp, Menu, X,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, useSyncExternalStore } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { tokenStore } from "@/lib/api";
import { cn } from "@/lib/utils";
import { useMe } from "./use-me";

const NAV = [
  { href: "/dashboard", label: "Обзор", icon: Gauge },
  { href: "/profile/import", label: "CV и профиль", icon: FileUp },
  { href: "/goals", label: "Карьерные цели", icon: Target },
  { href: "/jobs", label: "Вакансии", icon: Briefcase },
  { href: "/skill-gap", label: "Skill Gap", icon: TrendingUp },
  { href: "/roadmap", label: "Roadmap", icon: Map },
  { href: "/learning", label: "Материалы", icon: BookOpen },
  { href: "/challenges", label: "Задания", icon: ClipboardCheck },
  { href: "/settings", label: "Настройки", icon: Settings },
];

const subscribe = () => () => {};

export function AppShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  // token lives in localStorage → read only on the client
  const hasToken = useSyncExternalStore(subscribe, () => !!tokenStore.get(), () => false);
  const me = useMe(hasToken);

  useEffect(() => {
    if (!tokenStore.get()) router.replace("/login");
  }, [router]);

  const logout = () => {
    tokenStore.clear();
    qc.clear();
    router.push("/");
  };

  if (!hasToken) return null;

  const nav = (
    <nav className="flex flex-col gap-1">
      {NAV.map(({ href, label, icon: Icon }) => (
        <Link
          key={href}
          href={href}
          onClick={() => setOpen(false)}
          className={cn(
            "flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors hover:bg-muted",
            pathname.startsWith(href) && "bg-muted font-medium",
          )}
        >
          <Icon className="size-4" />
          {label}
        </Link>
      ))}
    </nav>
  );

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-60 shrink-0 flex-col border-r p-4 md:flex">
        <Link href="/dashboard" className="mb-6 px-3 text-lg font-semibold">SkillBridge</Link>
        {nav}
        <div className="mt-auto space-y-2 px-3 pt-6 text-xs text-muted-foreground">
          {me.data && (
            <div className="truncate" title={me.data.email}>
              {me.data.profile.fullName ?? me.data.email}
              {me.data.demo && <span className="ml-1 rounded bg-amber-100 px-1 text-amber-900">демо</span>}
            </div>
          )}
          <Button variant="ghost" size="sm" className="-ml-2" onClick={logout}>
            <LogOut /> Выйти
          </Button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b px-4 py-3 md:hidden">
          <Link href="/dashboard" className="font-semibold">SkillBridge</Link>
          <Button variant="ghost" size="icon" onClick={() => setOpen((o) => !o)} aria-label="Меню">
            {open ? <X /> : <Menu />}
          </Button>
        </header>
        {open && (
          <div className="border-b p-4 md:hidden">
            {nav}
            <Button variant="ghost" size="sm" className="mt-2" onClick={logout}><LogOut /> Выйти</Button>
          </div>
        )}
        <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8 sm:px-6">{children}</main>
      </div>
    </div>
  );
}
