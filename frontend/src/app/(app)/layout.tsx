import { Suspense } from "react";
import { AppShell } from "@/components/app/app-shell";

// AppShell reads the URL (usePathname/useParams) and the token from localStorage,
// so it streams in after the static shell.
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <Suspense fallback={<div className="p-8 text-sm text-muted-foreground">Загрузка…</div>}>
      <AppShell>{children}</AppShell>
    </Suspense>
  );
}
