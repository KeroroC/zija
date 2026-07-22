import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authApi } from "../api/auth";
import { invitationApi } from "../api/invitation";
import InvitationRedeemPage from "./InvitationRedeemPage.vue";

const pushMock = vi.fn();
const applySessionMock = vi.fn();
const ensureInitializedMock = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock })
}));

vi.mock("../api/auth", () => ({
  authApi: { initializeCsrf: vi.fn() }
}));

vi.mock("../api/invitation", () => ({
  invitationApi: {
    inspect: vi.fn(),
    redeem: vi.fn()
  }
}));

vi.mock("../stores/session", () => ({
  useSessionStore: () => ({
    applySession: applySessionMock,
    ensureInitialized: ensureInitializedMock
  })
}));

const sessionInfo = {
  authenticated: true,
  accountId: "account-2",
  username: "member",
  displayName: "成员"
};

const inspectMock = vi.mocked(invitationApi.inspect);
const redeemMock = vi.mocked(invitationApi.redeem);

describe("InvitationRedeemPage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    pushMock.mockReset();
    applySessionMock.mockReset().mockResolvedValue(true);
    ensureInitializedMock.mockReset().mockResolvedValue(undefined);
    vi.mocked(authApi.initializeCsrf).mockReset().mockResolvedValue(undefined);
    inspectMock.mockReset().mockResolvedValue({
      valid: true,
      householdName: "我的家",
      role: "MEMBER"
    });
    redeemMock.mockReset().mockResolvedValue(sessionInfo);
    window.history.replaceState(null, "", "/invitation/redeem#token=invite-token");
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("applies the SessionInfo returned by redeem without re-initializing", async () => {
    wrapper = mount(InvitationRedeemPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("member");
    await inputs[1].setValue("secret");
    await inputs[2].setValue("成员");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(redeemMock).toHaveBeenCalledWith("invite-token", expect.objectContaining({
      username: "member",
      displayName: "成员"
    }));
    expect(applySessionMock).toHaveBeenCalledWith(sessionInfo);
    expect(ensureInitializedMock).not.toHaveBeenCalled();
    expect(pushMock).toHaveBeenCalledWith({ name: "home" });
  });

  it("navigates so the router can retry session synchronization when it is incomplete", async () => {
    applySessionMock.mockResolvedValue(false);
    wrapper = mount(InvitationRedeemPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("member");
    await inputs[1].setValue("secret");
    await inputs[2].setValue("成员");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(pushMock).toHaveBeenCalledWith({ name: "home" });
  });
});
