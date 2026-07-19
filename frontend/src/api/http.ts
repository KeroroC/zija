import type { ApiProblem } from "../types/system";

export class ApiError extends Error {
  readonly errorCode: string;
  readonly requestId?: string;

  constructor(
    message: string,
    errorCode: string,
    requestId?: string
  ) {
    super(message);
    this.name = "ApiError";
    this.errorCode = errorCode;
    this.requestId = requestId;
  }
}

export async function getJson<T>(path: string): Promise<T> {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "";
  const response = await fetch(baseUrl + path, {
    credentials: "same-origin",
    headers: {
      Accept: "application/json"
    }
  });

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
    problem.requestId ?? response.headers.get("X-Request-Id") ?? undefined
  );
}
