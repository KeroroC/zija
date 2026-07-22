import { beforeEach, describe, expect, it, vi } from "vitest";
import type { SessionInfo } from "../types/identity";
import { authApi } from "./auth";
import { householdApi } from "./household";
import { postJsonAndRefreshCsrf } from "./http";
import { invitationApi } from "./invitation";
import { ownerRecoveryApi } from "./owner-recovery";

vi.mock("./http", () => ({
  clearCsrf: vi.fn(),
  ensureCsrf: vi.fn(),
  getJson: vi.fn(),
  postJson: vi.fn(),
  postJsonAndRefreshCsrf: vi.fn(),
  putJson: vi.fn()
}));

const postJsonAndRefreshCsrfMock = vi.mocked(postJsonAndRefreshCsrf);
const sessionInfo: SessionInfo = {
  authenticated: true,
  accountId: "account-1",
  username: "owner",
  displayName: "户主"
};

describe("CSRF-rotating API wiring", () => {
  beforeEach(() => {
    postJsonAndRefreshCsrfMock.mockReset();
  });

  it("uses the refresh helper for login", async () => {
    const request = { username: "owner", password: "secret" };
    postJsonAndRefreshCsrfMock.mockResolvedValue(sessionInfo);

    await expect(authApi.login(request)).resolves.toEqual(sessionInfo);

    expect(postJsonAndRefreshCsrfMock).toHaveBeenCalledWith(
      "/api/v1/auth/login",
      request
    );
  });

  it("uses the refresh helper for logout", async () => {
    postJsonAndRefreshCsrfMock.mockResolvedValue(undefined);

    await authApi.logout();

    expect(postJsonAndRefreshCsrfMock).toHaveBeenCalledWith("/api/v1/auth/logout");
  });

  it("uses the refresh helper for household bootstrap", async () => {
    const request = {
      householdName: "我的家",
      username: "owner",
      password: "secret",
      displayName: "户主"
    };
    postJsonAndRefreshCsrfMock.mockResolvedValue(sessionInfo);

    await expect(householdApi.bootstrap(request)).resolves.toEqual(sessionInfo);

    expect(postJsonAndRefreshCsrfMock).toHaveBeenCalledWith(
      "/api/v1/household/bootstrap",
      request
    );
  });

  it("uses the refresh helper and returns SessionInfo for invitation redemption", async () => {
    const request = {
      username: "member",
      password: "secret",
      displayName: "成员",
      email: "member@example.com"
    };
    postJsonAndRefreshCsrfMock.mockResolvedValue(sessionInfo);

    const result: SessionInfo = await invitationApi.redeem("invite-token", request);

    expect(result).toEqual(sessionInfo);
    expect(postJsonAndRefreshCsrfMock).toHaveBeenCalledWith(
      "/api/v1/invitations/redeem",
      { token: "invite-token", ...request }
    );
  });

  it("uses the refresh helper for owner password reset", async () => {
    const request = { token: "recovery-token", newPassword: "new-secret" };
    postJsonAndRefreshCsrfMock.mockResolvedValue(undefined);

    await ownerRecoveryApi.resetPassword(request);

    expect(postJsonAndRefreshCsrfMock).toHaveBeenCalledWith(
      "/api/v1/owner-recovery/reset-password",
      request
    );
  });
});
