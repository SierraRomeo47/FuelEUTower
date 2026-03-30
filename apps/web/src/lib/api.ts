export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082";

export function apiUrl(path: string) {
  if (!path.startsWith("/")) return `${API_BASE_URL}/${path}`;
  return `${API_BASE_URL}${path}`;
}

