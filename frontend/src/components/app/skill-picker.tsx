"use client";

import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { api } from "@/lib/api";
import type { Skill, SkillGapResponse } from "@/lib/types";

/** Skill selector; defaults to the user's top-priority gap when a goal exists. */
export function SkillPicker({ value, onChange }: { value: string; onChange: (slug: string) => void }) {
  const catalog = useQuery({ queryKey: ["skills"], queryFn: () => api<Skill[]>("/skills") });
  const gap = useQuery({ queryKey: ["skill-gap"], queryFn: () => api<SkillGapResponse>("/me/skill-gap"), retry: false });

  useEffect(() => {
    if (value) return;
    const top = gap.data?.skills.find((s) => s.gap > 0)?.skill.slug;
    if (top) onChange(top);
    else if (gap.isError && catalog.data?.length) onChange("sql");
  }, [value, gap.data, gap.isError, catalog.data, onChange]);

  const gapSlugs = new Set(gap.data?.skills.filter((s) => s.gap > 0).map((s) => s.skill.slug));
  return (
    <select className="h-8 rounded-lg border bg-background px-2.5 text-sm" value={value} onChange={(e) => onChange(e.target.value)}
      aria-label="Навык">
      {!value && <option value="">Выберите навык</option>}
      {gapSlugs.size > 0 && (
        <optgroup label="Ваши пробелы">
          {gap.data!.skills.filter((s) => s.gap > 0).map((s) => <option key={s.skill.slug} value={s.skill.slug}>{s.skill.name}</option>)}
        </optgroup>
      )}
      <optgroup label="Все навыки">
        {catalog.data?.filter((s) => !gapSlugs.has(s.slug)).map((s) => <option key={s.slug} value={s.slug}>{s.name}</option>)}
      </optgroup>
    </select>
  );
}
