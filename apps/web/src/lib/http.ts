import { apiUrl } from "@/lib/api";

export async function fetchJson(path: string) {
  const res = await fetch(apiUrl(path), { cache: "no-store" });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  return res.json();
}

export function asArray<T = any>(payload: any): T[] {
  if (Array.isArray(payload)) return payload as T[];
  if (Array.isArray(payload?.items)) return payload.items as T[];
  if (Array.isArray(payload?.rows)) return payload.rows as T[];
  if (Array.isArray(payload?.data)) return payload.data as T[];
  return [];
}
