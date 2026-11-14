"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Me } from "@/lib/types";

export function useMe(enabled = true) {
  return useQuery({ queryKey: ["me"], queryFn: () => api<Me>("/me"), enabled });
}
