import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authApi } from "../api/auth";
import { invitationApi } from "../api/invitation";
import InvitationRedeemPage from "./InvitationRedeemPage.vue";

const pushMock = vi.fn();
const applySessionMock = vi.fn();
const ensureInitializedMock = vi.fn();
const logoutMock = vi.fn();

const sessionState = {
  applySession: applySessionMock,
  ensureInitialized: ensureInitializedMock,
  logout: logoutMock,
  authenticated: false,
  currentMember: null as null | {
    householdId: string;
    memberId: string;
    accountId: string;
    username: string;
    displayName: string;
    role: string;
    status: string;
    householdName: string;
  },
  session: null as null | {
    authenticated: boolean;
    accountId?: string;
    username?: string;
    displayName?: string;
  }
};

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
  useSessionStore: () => sessionState
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
    sessionStorage.clear();
    pushMock.mockReset();
    applySessionMock.mockReset().mockResolvedValue(true);
    ensureInitializedMock.mockReset().mockResolvedValue(undefined);
    logoutMock.mockReset().mockResolvedValue(undefined);
    sessionState.authenticated = false;
    sessionState.currentMember = null;
    sessionState.session = null;
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
    // ensureInitialized runs once during mount, redeem path must not call it again
    expect(ensureInitializedMock).toHaveBeenCalledTimes(1);
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

  it("renders an already-logged-in notice instead of the form when the session is authenticated", async () => {
    sessionState.authenticated = true;
    sessionState.currentMember = {
      householdId: "h1",
      memberId: "m1",
      accountId: "a1",
      username: "admin",
      displayName: "管理员",
      role: "ADMIN",
      status: "ACTIVE",
      householdName: "原家庭"
    };
    sessionState.session = {
      authenticated: true,
      accountId: "a1",
      username: "admin",
      displayName: "管理员"
    };

    wrapper = mount(InvitationRedeemPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    expect(wrapper.text()).toContain("你当前已登录");
    expect(wrapper.text()).toContain("管理员");
    expect(wrapper.findAll("input").length).toBe(0);
    expect(wrapper.text()).toContain("返回首页");
    expect(wrapper.text()).toContain("登出并继续");
  });

  it("calls logout and lets reactivity switch the view back to the form", async () => {
    sessionState.authenticated = true;
    sessionState.currentMember = {
      householdId: "h1",
      memberId: "m1",
      accountId: "a1",
      username: "admin",
      displayName: "管理员",
      role: "ADMIN",
      status: "ACTIVE",
      householdName: "原家庭"
    };
    sessionState.session = {
      authenticated: true,
      accountId: "a1",
      username: "admin",
      displayName: "管理员"
    };

    logoutMock.mockImplementation(async () => {
      sessionState.authenticated = false;
      sessionState.currentMember = null;
      sessionState.session = null;
    });

    wrapper = mount(InvitationRedeemPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();
    expect(wrapper.text()).toContain("你当前已登录");

    const logoutBtn = wrapper.findAll("button").find(b => b.text().includes("登出并继续"))!;
    expect(logoutBtn).toBeDefined();
    await logoutBtn.trigger("click");
    await flushPromises();

    expect(logoutMock).toHaveBeenCalledTimes(1);
    expect(wrapper.findAll("input").length).toBe(4);
    expect(wrapper.text()).toContain("加入");
    expect(pushMock).not.toHaveBeenCalled();
  });
});
