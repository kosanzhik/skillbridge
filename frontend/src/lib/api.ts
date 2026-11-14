export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080/api";
const TOKEN_KEY = "sb_token";

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string | undefined,
    message: string,
    public errors?: Record<string, string>,
  ) {
    super(message);
  }
}

export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string) {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      /* storage unavailable */
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* storage unavailable */
    }
  },
};

type Options = { method?: string; body?: unknown; form?: FormData };

export async function api<T>(path: string, opts: Options = {}): Promise<T> {
  const headers: Record<string, string> = {};
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  let body: BodyInit | undefined;
  if (opts.form) {
    body = opts.form;
  } else if (opts.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(opts.body);
  }
  const res = await fetch(`${API_URL}${path}`, {
    method: opts.method ?? (body ? "POST" : "GET"),
    headers,
    body,
  });
  if (res.status === 401 && token) {
    tokenStore.clear();
    if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
      // outside React (plain fetch wrapper): a hard redirect also drops cached user data
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.href = "/login";
    }
  }
  if (!res.ok) {
    let data: { detail?: string; code?: string; errors?: Record<string, string> } = {};
    try {
      data = await res.json();
    } catch {
      /* empty body */
    }
    throw new ApiError(res.status, data.code, data.detail ?? `Ошибка ${res.status}`, data.errors);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

/** Fetches a protected binary (e.g. the CV PDF) and returns an object URL. */
export async function apiBlobUrl(path: string): Promise<string> {
  const token = tokenStore.get();
  const res = await fetch(`${API_URL}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) throw new ApiError(res.status, undefined, `Ошибка ${res.status}`);
  return URL.createObjectURL(await res.blob());
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.errors && Object.keys(e.errors).length) {
      return Object.entries(e.errors).map(([k, v]) => `${k}: ${v}`).join("; ");
    }
    return e.message;
  }
  return e instanceof Error ? e.message : "Неизвестная ошибка";
}
