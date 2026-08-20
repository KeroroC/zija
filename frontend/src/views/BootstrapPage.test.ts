import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authApi } from "../api/auth";
import { householdApi } from "../api/household";
import BootstrapPage from "./BootstrapPage.vue";

const pushMock = vi.fn();
const applySessionMock = vi.fn();
const ensureInitializedMock = vi.fn();
const sessionStoreMock = {
  householdInitialized: false,
  applySession: applySessionMock,
  ensureInitialized: ensureInitializedMock
};

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock })
}));

vi.mock("../api/auth", () => ({
  authApi: { initializeCsrf: vi.fn() }
}));

vi.mock("../api/household", () => ({
  householdApi: { bootstrap: vi.fn(), getStatus: vi.fn() }
}));

vi.mock("../stores/session", () => ({
  useSessionStore: () => sessionStoreMock
}));

const sessionInfo = {
  authenticated: true,
  accountId: "account-1",
  username: "owner",
  displayName: "户主"
};

const bootstrapMock = vi.mocked(householdApi.bootstrap);
const getStatusMock = vi.mocked(householdApi.getStatus);

describe("BootstrapPage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    pushMock.mockReset();
    applySessionMock.mockReset().mockResolvedValue(true);
    ensureInitializedMock.mockReset().mockResolvedValue(undefined);
    vi.mocked(authApi.initializeCsrf).mockReset().mockResolvedValue(undefined);
    bootstrapMock.mockReset().mockResolvedValue(sessionInfo);
    getStatusMock.mockReset().mockResolvedValue({ initialized: false, setupTokenRequired: false });
    sessionStoreMock.householdInitialized = false;
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("applies the SessionInfo returned by bootstrap without re-initializing", async () => {
    wrapper = mount(BootstrapPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("我的家");
    await inputs[1].setValue("owner");
    await inputs[2].setValue("secret");
    await inputs[3].setValue("户主");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(applySessionMock).toHaveBeenCalledWith(sessionInfo);
    expect(ensureInitializedMock).not.toHaveBeenCalled();
    expect(sessionStoreMock.householdInitialized).toBe(true);
    expect(pushMock).toHaveBeenCalledWith({ name: "home" });
  });

  it("keeps the created household initialized and navigates when session sync is incomplete", async () => {
    applySessionMock.mockResolvedValue(false);
    wrapper = mount(BootstrapPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("我的家");
    await inputs[1].setValue("owner");
    await inputs[2].setValue("secret");
    await inputs[3].setValue("户主");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(sessionStoreMock.householdInitialized).toBe(true);
    expect(pushMock).toHaveBeenCalledWith({ name: "home" });
  });

  it("sends setup token when bootstrap requires it", async () => {
    getStatusMock.mockResolvedValue({ initialized: false, setupTokenRequired: true });
    wrapper = mount(BootstrapPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("setup-secret");
    await inputs[1].setValue("我的家");
    await inputs[2].setValue("owner");
    await inputs[3].setValue("secret");
    await inputs[4].setValue("户主");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(bootstrapMock).toHaveBeenCalledWith(
      expect.objectContaining({ householdName: "我的家", username: "owner" }),
      "setup-secret"
    );
  });
});
