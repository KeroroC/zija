import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { authApi } from "../api/auth";
import { householdApi } from "../api/household";
import { clearCsrf } from "../api/http";
import type { CurrentMember, SessionInfo } from "../types/identity";
import { useSessionStore } from "./session";

vi.mock("../api/auth", () => ({
  authApi: {
    initializeCsrf: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    getSession: vi.fn()
  }
}));

vi.mock("../api/household", () => ({
  householdApi: {
    getStatus: vi.fn(),
    getCurrentMember: vi.fn()
  }
}));

vi.mock("../api/http", () => ({
  clearCsrf: vi.fn()
}));

const authenticatedSession: SessionInfo = {
  authenticated: true,
  accountId: "account-1",
  username: "owner",
  displayName: "户主"
};

const currentMember: CurrentMember = {
  householdId: "household-1",
  memberId: "member-1",
  accountId: "account-1",
  username: "owner",
  displayName: "户主",
  role: "OWNER",
  status: "ACTIVE"
};

const initializeCsrfMock = vi.mocked(authApi.initializeCsrf);
const loginMock = vi.mocked(authApi.login);
const logoutMock = vi.mocked(authApi.logout);
const getSessionMock = vi.mocked(authApi.getSession);
const getStatusMock = vi.mocked(householdApi.getStatus);
const getCurrentMemberMock = vi.mocked(householdApi.getCurrentMember);
const clearCsrfMock = vi.mocked(clearCsrf);

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

describe("session store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    initializeCsrfMock.mockReset().mockResolvedValue(undefined);
    loginMock.mockReset();
    logoutMock.mockReset().mockResolvedValue(undefined);
    getSessionMock.mockReset();
    getStatusMock.mockReset();
    getCurrentMemberMock.mockReset().mockResolvedValue(currentMember);
    clearCsrfMock.mockReset();
  });

  it("applies an authenticated SessionInfo even when already initialized", async () => {
    const store = useSessionStore();
    store.initialized = true;
    store.session = { authenticated: false };

    await expect(store.applySession(authenticatedSession)).resolves.toBe(true);

    expect(store.session).toEqual(authenticatedSession);
    expect(store.currentMember).toEqual(currentMember);
    expect(store.initialized).toBe(true);
  });

  it("clears the current member when applying an anonymous SessionInfo", async () => {
    const store = useSessionStore();
    store.initialized = true;
    store.currentMember = currentMember;

    await expect(store.applySession({ authenticated: false })).resolves.toBe(true);

    expect(store.session).toEqual({ authenticated: false });
    expect(store.currentMember).toBeNull();
    expect(getCurrentMemberMock).not.toHaveBeenCalled();
  });

  it("does not commit an authenticated session when member loading fails", async () => {
    const store = useSessionStore();
    const previousMember = currentMember;
    store.initialized = true;
    store.session = { authenticated: false };
    store.currentMember = previousMember;
    getCurrentMemberMock.mockRejectedValue(new Error("member unavailable"));

    await expect(store.applySession(authenticatedSession)).resolves.toBe(false);

    expect(store.session).toBeNull();
    expect(store.currentMember).toBeNull();
    expect(store.initialized).toBe(false);
  });

  it("does not let an older failed member synchronization clear a newer session", async () => {
    const store = useSessionStore();
    const olderMember = deferred<CurrentMember>();
    const newerMember = { ...currentMember, memberId: "member-2", displayName: "新成员" };
    const newerSession = { ...authenticatedSession, accountId: "account-2", username: "new-owner" };
    getCurrentMemberMock
      .mockReturnValueOnce(olderMember.promise)
      .mockResolvedValueOnce(newerMember);

    const olderApply = store.applySession(authenticatedSession);
    await expect(store.applySession(newerSession)).resolves.toBe(true);
    olderMember.reject(new Error("stale member unavailable"));

    await expect(olderApply).resolves.toBe(false);
    expect(store.session).toEqual(newerSession);
    expect(store.currentMember).toEqual(newerMember);
    expect(store.initialized).toBe(true);
  });

  it("does not let a pending member synchronization restore a cleared local session", async () => {
    const store = useSessionStore();
    const pendingMember = deferred<CurrentMember>();
    getCurrentMemberMock.mockReturnValueOnce(pendingMember.promise);

    const pendingApply = store.applySession(authenticatedSession);
    store.clearLocalSession();
    pendingMember.resolve(currentMember);

    await expect(pendingApply).resolves.toBe(false);
    expect(store.session).toBeNull();
    expect(store.currentMember).toBeNull();
    expect(store.initialized).toBe(true);
  });

  it("lets ensureInitialized retry after a failed member synchronization", async () => {
    const store = useSessionStore();
    getStatusMock.mockResolvedValue({ initialized: true });
    getSessionMock.mockResolvedValue(authenticatedSession);
    getCurrentMemberMock
      .mockRejectedValueOnce(new Error("member unavailable"))
      .mockResolvedValueOnce(currentMember);

    await expect(store.ensureInitialized()).resolves.toBe(false);
    expect(store.initialized).toBe(false);
    expect(store.session).toBeNull();

    await expect(store.ensureInitialized()).resolves.toBe(true);
    expect(store.initialized).toBe(true);
    expect(store.session).toEqual(authenticatedSession);
    expect(store.currentMember).toEqual(currentMember);
    expect(getStatusMock).toHaveBeenCalledTimes(2);
  });

  it("synchronizes the current member after login", async () => {
    const store = useSessionStore();
    loginMock.mockResolvedValue(authenticatedSession);

    await expect(store.login("owner", "secret")).resolves.toBe(true);

    expect(store.session).toEqual(authenticatedSession);
    expect(store.currentMember).toEqual(currentMember);
  });

  it("keeps a successful login result non-throwing when member synchronization fails", async () => {
    const store = useSessionStore();
    loginMock.mockResolvedValue(authenticatedSession);
    getCurrentMemberMock.mockRejectedValue(new Error("member unavailable"));

    await expect(store.login("owner", "secret")).resolves.toBe(false);

    expect(store.session).toBeNull();
    expect(store.currentMember).toBeNull();
    expect(store.initialized).toBe(false);
  });

  it("keeps the CSRF token refreshed by a successful logout", async () => {
    const store = useSessionStore();
    store.session = authenticatedSession;
    store.currentMember = currentMember;

    await store.logout();

    expect(clearCsrfMock).not.toHaveBeenCalled();
    expect(store.session).toBeNull();
    expect(store.currentMember).toBeNull();
  });

  it("keeps local state while propagating a failed logout and clears cached CSRF", async () => {
    const store = useSessionStore();
    const failure = new Error("logout unavailable");
    store.session = authenticatedSession;
    store.currentMember = currentMember;
    logoutMock.mockRejectedValue(failure);

    await expect(store.logout()).rejects.toBe(failure);

    expect(clearCsrfMock).toHaveBeenCalledOnce();
    expect(store.session).toEqual(authenticatedSession);
    expect(store.currentMember).toEqual(currentMember);
  });

  it("clears only local session state for direct navigation to login", () => {
    const store = useSessionStore();
    store.householdInitialized = true;
    store.initialized = false;
    store.session = authenticatedSession;
    store.currentMember = currentMember;

    store.clearLocalSession();

    expect(store.session).toBeNull();
    expect(store.currentMember).toBeNull();
    expect(store.initialized).toBe(true);
    expect(store.householdInitialized).toBe(true);
    expect(clearCsrfMock).toHaveBeenCalledOnce();
    expect(logoutMock).not.toHaveBeenCalled();
  });
});
