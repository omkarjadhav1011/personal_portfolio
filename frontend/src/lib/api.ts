import { getAuthToken, clearAuthToken } from "@/store/auth";

/** Backend base URL. Empty in dev — relative paths hit the Vite proxy. */
const BASE_URL = import.meta.env.VITE_API_URL ?? "";

/**
 * Resolves a backend asset path (e.g. "/api/profile/avatar") to an absolute URL.
 * Needed for `<img src>` and other browser-issued requests that bypass {@link apiFetch}:
 * in production the frontend (Vercel) and backend (Render) are different origins, so a
 * bare relative path would resolve against the frontend. Pass-through for blob:/data:/
 * absolute URLs and falsy values.
 */
export function assetUrl(path?: string | null): string | undefined {
  if (!path) return undefined;
  if (/^(https?:|blob:|data:)/.test(path)) return path;
  return `${BASE_URL}${path}`;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly body?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/**
 * Typed fetch wrapper for the backend.
 * - Prefixes {@link BASE_URL} (proxy in dev, absolute backend URL in prod).
 * - Attaches `Authorization: Bearer <token>` when the auth store holds one.
 * - Serializes/deserializes JSON and throws {@link ApiError} on non-2xx.
 */
export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const token = getAuthToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });

  if (!res.ok) {
    // Token expired/invalid → drop it so RequireAuth bounces to login.
    if (res.status === 401) {
      clearAuthToken();
    }
    let body: unknown;
    try {
      body = await res.json();
    } catch {
      // Non-JSON error body — ignore and fall back to status text.
    }
    throw new ApiError(res.status, extractMessage(body, res), body);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

/**
 * Raw fetch with the Bearer token + base URL attached, returning the Response
 * unparsed (caller branches on res.ok / res.json()). Used by ported admin code.
 */
export async function authFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const headers = new Headers(options.headers);
  const token = getAuthToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });
  // Token expired/invalid → drop it so RequireAuth bounces to login.
  if (res.status === 401) {
    clearAuthToken();
  }
  return res;
}

/**
 * Pulls a human message from an error body. Handles both a top-level
 * `{ message }` and the backend's standardized `{ error: { code, message } }`.
 */
function extractMessage(body: unknown, res: Response): string {
  if (body && typeof body === "object") {
    const b = body as Record<string, unknown>;
    if (typeof b.message === "string") return b.message;
    const err = b.error;
    if (err && typeof err === "object" && typeof (err as Record<string, unknown>).message === "string") {
      return (err as Record<string, unknown>).message as string;
    }
  }
  return `Request failed: ${res.status} ${res.statusText}`;
}
