"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { api, tokenStore } from "@/lib/api";
import type { AuthResponse } from "@/lib/types";

export function useAuthActions() {
  const qc = useQueryClient();
  const router = useRouter();
  const finish = (res: AuthResponse) => {
    tokenStore.set(res.token);
    qc.clear();
    qc.setQueryData(["me"], res.user);
    router.push(res.user.profile.onboarded ? "/dashboard" : "/onboarding");
  };
  return {
    login: async (email: string, password: string) => finish(await api<AuthResponse>("/auth/login", { body: { email, password } })),
    register: async (email: string, password: string, fullName: string, consent: boolean) =>
      finish(await api<AuthResponse>("/auth/register", { body: { email, password, fullName, consent } })),
    demo: async () => finish(await api<AuthResponse>("/auth/demo", { method: "POST" })),
  };
}
