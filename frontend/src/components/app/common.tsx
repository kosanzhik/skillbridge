"use client";

import { AlertCircle, Inbox } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { errorMessage } from "@/lib/api";
import { SOURCE_LABEL } from "@/lib/labels";
import type { SkillSource } from "@/lib/types";
import { cn } from "@/lib/utils";

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted-foreground max-w-2xl">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function Loading({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-20 w-full" />
      ))}
    </div>
  );
}

export function ErrorState({ error }: { error: unknown }) {
  return (
    <Alert variant="destructive">
      <AlertCircle />
      <AlertTitle>Не удалось загрузить данные</AlertTitle>
      <AlertDescription>{errorMessage(error)}</AlertDescription>
    </Alert>
  );
}

export function Empty({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed p-10 text-center">
      <Inbox className="mb-3 size-8 text-muted-foreground" />
      <p className="font-medium">{title}</p>
      {children && <div className="mt-2 text-sm text-muted-foreground max-w-md">{children}</div>}
    </div>
  );
}

/** Five dots: filled up to `level`, outlined marker at `target`. */
export function LevelDots({ level, target }: { level: number; target?: number }) {
  return (
    <span className="inline-flex items-center gap-1" aria-label={`Уровень ${level} из 5`}>
      {[1, 2, 3, 4, 5].map((i) => (
        <span
          key={i}
          className={cn(
            "size-2.5 rounded-full border",
            i <= level ? "border-primary bg-primary" : "border-muted-foreground/30",
            target != null && i === target && i > level && "border-amber-500 border-2",
          )}
        />
      ))}
    </span>
  );
}

export function SourceBadge({ source, proven }: { source: SkillSource | null; proven?: boolean }) {
  if (!source) return <Badge variant="outline">Нет в профиле</Badge>;
  return (
    <Badge variant={proven ? "default" : "secondary"} title={proven ? "Подтверждено практикой" : "Заявлено, не проверено"}>
      {SOURCE_LABEL[source]}
    </Badge>
  );
}

export function Stat({ label, value, hint }: { label: string; value: React.ReactNode; hint?: string }) {
  return (
    <div className="rounded-xl border p-4">
      <div className="text-xs uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className="mt-1 text-2xl font-semibold tabular-nums">{value}</div>
      {hint && <div className="mt-1 text-xs text-muted-foreground">{hint}</div>}
    </div>
  );
}
