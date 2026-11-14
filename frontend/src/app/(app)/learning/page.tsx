"use client";

import { useQuery } from "@tanstack/react-query";
import { ExternalLink } from "lucide-react";
import { useState } from "react";
import { Empty, ErrorState, Loading, PageHeader } from "@/components/app/common";
import { SkillPicker } from "@/components/app/skill-picker";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/labels";
import type { LearningResource } from "@/lib/types";

const TYPE: Record<string, string> = { COURSE: "Курс", VIDEO: "Видео", ARTICLE: "Статья", DOCS: "Документация", BOOK: "Книга" };

export default function LearningPage() {
  const [skill, setSkill] = useState("");
  const q = useQuery({
    queryKey: ["resources", skill], enabled: !!skill,
    queryFn: () => api<LearningResource[]>(`/learning/resources?skill=${encodeURIComponent(skill)}`),
  });
  return (
    <>
      <PageHeader title="Учебные материалы"
        description="Только реальные курсы, статьи и документация с указанием источника. Ссылки регулярно проверяются на доступность."
        actions={<SkillPicker value={skill} onChange={setSkill} />} />
      {q.isLoading && <Loading />}
      {q.error && <ErrorState error={q.error} />}
      {q.data && q.data.length === 0 && <Empty title="Для этого навыка пока нет проверенных материалов" />}
      <div className="grid gap-3 md:grid-cols-2">
        {q.data?.map((r) => (
          <Card key={r.id}>
            <CardContent className="space-y-2">
              <a href={r.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 font-medium underline">
                {r.title} <ExternalLink className="size-3" />
              </a>
              <div className="text-sm text-muted-foreground">{r.provider}</div>
              <div className="flex flex-wrap gap-1.5">
                <Badge variant="secondary">{TYPE[r.type] ?? r.type}</Badge>
                {r.level != null && <Badge variant="outline">уровень {r.level}</Badge>}
                {r.free && <Badge variant="outline">бесплатно</Badge>}
                {r.language && <Badge variant="outline">{r.language.toUpperCase()}</Badge>}
              </div>
              <div className="text-xs text-muted-foreground">Ссылка проверена: {formatDate(r.verifiedAt)}</div>
            </CardContent>
          </Card>
        ))}
      </div>
    </>
  );
}
