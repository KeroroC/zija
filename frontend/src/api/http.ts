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
let csrfGeneration = 0;
let csrfPromise: { generation: number; promise: Promise<void> } | null = null;

function baseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL ?? "";
}

export function getCookie(name: string): string | null {
  const match = document.cookie.match(
    new RegExp("(?:^|; )" + name.replace(/([.$?*|{}()[\]\\/+^])/g, "\\$1") + "=([^;]*)")
  );
  return match ? decodeURIComponent(match[1]) : null;
}

export async function ensureCsrf(): Promise<void> {
  if (csrfToken) return;
  if (csrfPromise?.generation === csrfGeneration) return csrfPromise.promise;

  const generation = csrfGeneration;
  const promise = fetch(baseUrl() + "/api/v1/auth/csrf", {
    credentials: "same-origin",
    headers: { Accept: "application/json" }
  })
    .then(async (res) => {
      if (!res.ok) {
        throw new ApiError(
          "CSRF request failed",
          "csrf_fetch_failed",
          res.status,
          res.headers.get("X-Request-Id") ?? undefined
        );
      }

      let data: unknown;
      try {
        data = await res.json();
      } catch {
        data = null;
      }
      if (
        !data
        || typeof data !== "object"
        || typeof (data as { token?: unknown }).token !== "string"
        || (data as { token: string }).token.trim() === ""
      ) {
        throw new ApiError(
          "CSRF response did not include a valid token",
          "invalid_csrf_response",
          res.status,
          res.headers.get("X-Request-Id") ?? undefined
        );
      }
      if (generation === csrfGeneration) {
        csrfToken = (data as { token: string }).token;
      }
    })
    .finally(() => {
      if (csrfPromise?.generation === generation) {
        csrfPromise = null;
      }
    });
  csrfPromise = { generation, promise };
  return promise;
}

export function clearCsrf(): void {
  csrfGeneration += 1;
  csrfToken = null;
  csrfPromise = null;
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
    const text = await response.text();
    if (!text) {
      return undefined as T;
    }
    return JSON.parse(text) as T;
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

export async function postJsonAndRefreshCsrf<T>(
  path: string,
  body?: unknown
): Promise<T> {
  const result = await request<T>("POST", path, body);
  clearCsrf();
  try {
    await ensureCsrf();
  } catch {
    // The business POST succeeded. The next unsafe request will retry CSRF.
  }
  return result;
}

export async function putJson<T>(path: string, body?: unknown): Promise<T> {
  return request<T>("PUT", path, body);
}

export async function deleteJson<T>(path: string, body?: unknown): Promise<T> {
  return request<T>("DELETE", path, body);
}
