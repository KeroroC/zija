import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  clearCsrf,
  ensureCsrf,
  postJson,
  postJsonAndRefreshCsrf,
  postJsonWithIdempotency,
  putJson,
  putJsonWithIdempotency
} from "./http";

function jsonResponse(body: unknown, init?: ResponseInit): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
    ...init
  });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

describe("CSRF refresh", () => {
  beforeEach(() => {
    clearCsrf();
  });

  afterEach(() => {
    clearCsrf();
    vi.unstubAllGlobals();
  });

  it("treats successful empty 200 bodies as void", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "csrf-token" }))
      .mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await ensureCsrf();
    await expect(putJson<void>("/api/v1/members/1/role", { role: "ADMIN" }))
      .resolves.toBeUndefined();
    await expect(postJson<void>("/api/v1/owner-recovery/reset-password", {
      token: "raw",
      newPassword: "N3wPassw0rd!"
    })).resolves.toBeUndefined();

    // putJson and the second post both need CSRF already loaded.
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("rejects a non-successful CSRF response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(
      { title: "CSRF unavailable" },
      { status: 500, headers: { "X-Request-Id": "csrf-request" } }
    )));

    await expect(ensureCsrf()).rejects.toMatchObject({
      status: 500,
      requestId: "csrf-request"
    });
  });

  it.each([
    ["missing", {}],
    ["empty", { token: "" }],
    ["non-string", { token: 42 }]
  ])("rejects a CSRF response with a %s token", async (_, body) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(body)));

    await expect(ensureCsrf()).rejects.toMatchObject({
      status: 200,
      errorCode: "invalid_csrf_response"
    });
  });

  it("refreshes the token after a successful POST and uses it on the next POST", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "old-token" }))
      .mockResolvedValueOnce(jsonResponse({ authenticated: true }))
      .mockResolvedValueOnce(jsonResponse({ token: "new-token" }))
      .mockResolvedValueOnce(jsonResponse({ saved: true }));
    vi.stubGlobal("fetch", fetchMock);

    await ensureCsrf();
    const result = await postJsonAndRefreshCsrf<{ authenticated: boolean }>(
      "/api/v1/auth/login",
      { username: "owner", password: "secret" }
    );
    await postJson("/api/v1/items", { name: "冰箱" });

    expect(result).toEqual({ authenticated: true });
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(fetchMock.mock.calls[1][1]?.headers).toMatchObject({
      "X-XSRF-TOKEN": "old-token"
    });
    expect(fetchMock.mock.calls[2][0]).toBe("/api/v1/auth/csrf");
    expect(fetchMock.mock.calls[3][1]?.headers).toMatchObject({
      "X-XSRF-TOKEN": "new-token"
    });
  });

  it("propagates a failed POST without refreshing CSRF", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "old-token" }))
      .mockResolvedValueOnce(jsonResponse(
        {
          title: "Invalid credentials",
          errorCode: "invalid_credentials",
          requestId: "request-123"
        },
        { status: 401 }
      ))
      .mockResolvedValueOnce(jsonResponse({ saved: true }));
    vi.stubGlobal("fetch", fetchMock);

    await ensureCsrf();

    let error: unknown;
    try {
      await postJsonAndRefreshCsrf(
        "/api/v1/auth/login",
        { username: "owner", password: "wrong" }
      );
    } catch (caught) {
      error = caught;
    }
    await postJson("/api/v1/items", { name: "冰箱" });

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 401,
      errorCode: "invalid_credentials",
      requestId: "request-123"
    });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[2][0]).toBe("/api/v1/items");
    expect(fetchMock.mock.calls[2][1]?.headers).toMatchObject({
      "X-XSRF-TOKEN": "old-token"
    });
  });

  it.each([
    ["500 response", () => jsonResponse({ title: "CSRF unavailable" }, { status: 500 })],
    ["malformed response", () => jsonResponse({ token: "" })]
  ])("keeps a successful 204 POST successful after a %s and retries later", async (_, refreshFailure) => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "old-token" }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(refreshFailure())
      .mockResolvedValueOnce(jsonResponse({ token: "retry-token" }))
      .mockResolvedValueOnce(jsonResponse({ saved: true }));
    vi.stubGlobal("fetch", fetchMock);

    await ensureCsrf();

    await expect(postJsonAndRefreshCsrf<void>("/api/v1/auth/logout"))
      .resolves.toBeUndefined();
    await postJson("/api/v1/items", { name: "冰箱" });

    expect(fetchMock).toHaveBeenCalledTimes(5);
    expect(fetchMock.mock.calls[1][1]?.headers).toMatchObject({
      "X-XSRF-TOKEN": "old-token"
    });
    expect(fetchMock.mock.calls[2][0]).toBe("/api/v1/auth/csrf");
    expect(fetchMock.mock.calls[3][0]).toBe("/api/v1/auth/csrf");
    expect(fetchMock.mock.calls[4][1]?.headers).toMatchObject({
      "X-XSRF-TOKEN": "retry-token"
    });
  });

  it("does not let an older refresh overwrite the newest rotating POST token", async () => {
    const firstPost = deferred<Response>();
    const secondPost = deferred<Response>();
    const firstRefresh = deferred<Response>();
    const secondRefresh = deferred<Response>();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "old-token" }))
      .mockReturnValueOnce(firstPost.promise)
      .mockReturnValueOnce(secondPost.promise)
      .mockReturnValueOnce(firstRefresh.promise)
      .mockReturnValueOnce(secondRefresh.promise)
      .mockResolvedValueOnce(jsonResponse({ saved: true }));
    vi.stubGlobal("fetch", fetchMock);

    await ensureCsrf();
    const firstResult = postJsonAndRefreshCsrf<{ order: number }>("/api/v1/rotate/first");
    const secondResult = postJsonAndRefreshCsrf<{ order: number }>("/api/v1/rotate/second");
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));

    firstPost.resolve(jsonResponse({ order: 1 }));
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4));
    secondPost.resolve(jsonResponse({ order: 2 }));
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(5));

    secondRefresh.resolve(jsonResponse({ token: "newest-token" }));
    await expect(secondResult).resolves.toEqual({ order: 2 });
    firstRefresh.resolve(jsonResponse({ token: "stale-token" }));
    await expect(firstResult).resolves.toEqual({ order: 1 });
    await postJson("/api/v1/items", { name: "冰箱" });

    expect(fetchMock).toHaveBeenCalledTimes(6);
    expect(fetchMock.mock.calls[5][1]?.headers).toMatchObject({
      "X-XSRF-TOKEN": "newest-token"
    });
  });

  it("keeps the newest refresh promise registered when an older refresh finishes", async () => {
    const firstPost = deferred<Response>();
    const secondPost = deferred<Response>();
    const firstRefresh = deferred<Response>();
    const secondRefresh = deferred<Response>();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "old-token" }))
      .mockReturnValueOnce(firstPost.promise)
      .mockReturnValueOnce(secondPost.promise)
      .mockReturnValueOnce(firstRefresh.promise)
      .mockReturnValueOnce(secondRefresh.promise)
      .mockResolvedValueOnce(jsonResponse({ saved: true }));
    vi.stubGlobal("fetch", fetchMock);

    await ensureCsrf();
    const firstResult = postJsonAndRefreshCsrf<{ order: number }>("/api/v1/rotate/first");
    const secondResult = postJsonAndRefreshCsrf<{ order: number }>("/api/v1/rotate/second");
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    firstPost.resolve(jsonResponse({ order: 1 }));
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4));
    secondPost.resolve(jsonResponse({ order: 2 }));
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(5));

    firstRefresh.resolve(jsonResponse({ token: "stale-token" }));
    await expect(firstResult).resolves.toEqual({ order: 1 });
    const pendingNewestRefresh = ensureCsrf();
    expect(fetchMock).toHaveBeenCalledTimes(5);
    secondRefresh.resolve(jsonResponse({ token: "newest-token" }));
    await expect(pendingNewestRefresh).resolves.toBeUndefined();
    await expect(secondResult).resolves.toEqual({ order: 2 });
    await postJson("/api/v1/items", { name: "冰箱" });

    expect(fetchMock).toHaveBeenCalledTimes(6);
    expect(fetchMock.mock.calls[5][1]?.headers).toMatchObject({
      "X-XSRF-TOKEN": "newest-token"
    });
  });
});

describe("Idempotency-Key header", () => {
  beforeEach(() => {
    clearCsrf();
  });

  afterEach(() => {
    clearCsrf();
    vi.unstubAllGlobals();
  });

  it("sends Idempotency-Key header with postJsonWithIdempotency", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "csrf-token" }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    await postJsonWithIdempotency("/api/v1/x", { a: 1 }, "key-123");
    const init = fetchMock.mock.calls[1][1] as RequestInit;
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBe("key-123");
  });

  it("sends Idempotency-Key header with putJsonWithIdempotency", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "csrf-token" }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    await putJsonWithIdempotency("/api/v1/x/1", { b: 2 }, "key-456");
    const init = fetchMock.mock.calls[1][1] as RequestInit;
    expect((init.headers as Record<string, string>)["Idempotency-Key"]).toBe("key-456");
  });
});
