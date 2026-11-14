"use client";

import Link from "next/link";
import { ApiError } from "@/lib/api";
import { buttonVariants } from "@/components/ui/button";
import { Empty, ErrorState } from "./common";

/** Shows a friendly prompt when the API says no career goal is chosen yet. */
export function GoalAwareError({ error }: { error: unknown }) {
  if (error instanceof ApiError && error.code === "NO_CAREER_GOAL") {
    return (
      <Empty title="Сначала выберите целевую профессию">
        <Link href="/goals" className={buttonVariants({ className: "mt-3" })}>Выбрать цель</Link>
      </Empty>
    );
  }
  return <ErrorState error={error} />;
}
