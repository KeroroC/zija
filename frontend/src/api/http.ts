import type { ApiProblem } from "../types/system";

export class ApiError extends Error {
  readonly errorCode: string;
  readonly requestId?: string;
  readonly status: number;

  constructor(
    message: string,
    errorCode: string,
    status: number,
    requestId?: string
  ) {
    super(message);
    this.name = "ApiError";
    this.errorCode = errorCode;
    this.status = status;
    this.requestId = requestId;
  }
}

let csrfToken: string | null = null;
let csrfPromise: Promise<void> | null = null;

function baseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL ?? "";
}

function getCookie(name: string): string | null {
  const match = document.cookie.match(
    new RegExp("(?:^|; )" + name.replace(/([.$?*|{}()[\]\\/+^])/g, "\\$1") + "=([^;]*)")
  );
  return match ? decodeURIComponent(match[1]) : null;
}

export async function ensureCsrf(): Promise<void> {
  if (csrfToken) return;
  if (csrfPromise) return csrfPromise;
  csrfPromise = fetch(baseUrl() + "/api/v1/auth/csrf", {
    credentials: "same-origin",
    headers: { Accept: "application/json" }
  })
    .then((res) => res.json())
    .then((data: { token: string }) => {
      csrfToken = data.token;
    })
    .finally(() => {
      csrfPromise = null;
    });
  return csrfPromise;
}

export function clearCsrf(): void {
  csrfToken = null;
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown
): Promise<T> {
  if (method !== "GET") {
    await ensureCsrf();
  }
  const headers: Record<string, string> = {
    Accept: "application/json"
  };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  const cookieToken = getCookie("XSRF-TOKEN");
  if (cookieToken && method !== "GET") {
    headers["X-XSRF-TOKEN"] = cookieToken;
    csrfToken = cookieToken;
  } else if (csrfToken && method !== "GET") {
    headers["X-XSRF-TOKEN"] = csrfToken;
  }

  const response = await fetch(baseUrl() + path, {
    method,
    credentials: "same-origin",
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  });

  if (response.status === 204) {
    return undefined as T;
  }

  if (response.ok) {
    return response.json() as Promise<T>;
  }

  let problem: ApiProblem = {};
  try {
    problem = (await response.json()) as ApiProblem;
  } catch {
    problem = {};
  }

  throw new ApiError(
    problem.title ?? "Request failed",
    problem.errorCode ?? "http_error",
    response.status,
    problem.requestId ?? response.headers.get("X-Request-Id") ?? undefined
  );
}

export async function getJson<T>(path: string): Promise<T> {
  return request<T>("GET", path);
}

export async function postJson<T>(path: string, body?: unknown): Promise<T> {
  return request<T>("POST", path, body);
}

export async function putJson<T>(path: string, body?: unknown): Promise<T> {
  return request<T>("PUT", path, body);
}
