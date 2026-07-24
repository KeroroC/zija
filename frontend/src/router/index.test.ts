import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authApi } from "../api/auth";
import { householdApi } from "../api/household";
import { useSessionStore } from "../stores/session";
import type { CurrentMember, SessionInfo } from "../types/identity";
import { router } from "./index";

vi.mock("../api/auth", () => ({
  authApi: {
    getSession: vi.fn(),
    initializeCsrf: vi.fn(),
    login: vi.fn(),
    logout: vi.fn()
  }
}));

vi.mock("../api/household", () => ({
  householdApi: {
    getCurrentMember: vi.fn(),
    getStatus: vi.fn()
  }
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
  status: "ACTIVE",
  householdName: "测试家庭"
};

const getSessionMock = vi.mocked(authApi.getSession);
const getCurrentMemberMock = vi.mocked(householdApi.getCurrentMember);
const getStatusMock = vi.mocked(householdApi.getStatus);
let resetSequence = 0;

async function resetRouterAndPinia(): Promise<void> {
  setActivePinia(createPinia());
  getStatusMock.mockReset().mockResolvedValue({ initialized: false });
  getSessionMock.mockReset();
  getCurrentMemberMock.mockReset();
  await router.replace({ name: "login", query: { reset: String(++resetSequence) } });
  await router.isReady();
  setActivePinia(createPinia());
  getStatusMock.mockReset();
  getSessionMock.mockReset();
  getCurrentMemberMock.mockReset();
}

describe("router session recovery", () => {
  beforeEach(async () => {
    await resetRouterAndPinia();
  });

  afterEach(async () => {
    await resetRouterAndPinia();
  });

  it("retries member synchronization in the guard after a failed initial applySession", async () => {
    const session = useSessionStore();
    getCurrentMemberMock
      .mockRejectedValueOnce(new Error("member unavailable"))
      .mockResolvedValueOnce(currentMember);
    getStatusMock.mockResolvedValue({ initialized: true });
    getSessionMock.mockResolvedValue(authenticatedSession);

    await expect(session.applySession(authenticatedSession)).resolves.toBe(false);
    expect(session.initialized).toBe(false);
    expect(session.authenticated).toBe(false);

    await router.push({ name: "home", query: { recovery: String(++resetSequence) } });

    expect(router.currentRoute.value.name).toBe("home");
    expect(session.initialized).toBe(true);
    expect(session.authenticated).toBe(true);
    expect(session.currentMember).toEqual(currentMember);
    expect(getStatusMock).toHaveBeenCalledOnce();
    expect(getSessionMock).toHaveBeenCalledOnce();
    expect(getCurrentMemberMock).toHaveBeenCalledTimes(2);
  });
});
