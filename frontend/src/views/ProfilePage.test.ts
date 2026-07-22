import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authApi } from "../api/auth";
import ProfilePage from "./ProfilePage.vue";

const pushMock = vi.fn();
const clearLocalSessionMock = vi.fn();
const logoutMock = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock })
}));

vi.mock("../api/auth", () => ({
  authApi: { changePassword: vi.fn() }
}));

vi.mock("../stores/session", () => ({
  useSessionStore: () => ({
    clearLocalSession: clearLocalSessionMock,
    logout: logoutMock
  })
}));

const changePasswordMock = vi.mocked(authApi.changePassword);

describe("ProfilePage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    pushMock.mockReset();
    clearLocalSessionMock.mockReset();
    logoutMock.mockReset();
    changePasswordMock.mockReset().mockResolvedValue(undefined);
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("clears only the local session and navigates to login after changing password", async () => {
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("old-secret");
    await inputs[1].setValue("new-secret");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(changePasswordMock).toHaveBeenCalledWith({
      currentPassword: "old-secret",
      newPassword: "new-secret"
    });
    expect(logoutMock).not.toHaveBeenCalled();
    expect(clearLocalSessionMock).toHaveBeenCalledOnce();
    expect(pushMock).toHaveBeenCalledWith({ name: "login" });
  });

  it("keeps the local session and current route when changing password fails", async () => {
    changePasswordMock.mockRejectedValue(new Error("password change failed"));
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("old-secret");
    await inputs[1].setValue("new-secret");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(clearLocalSessionMock).not.toHaveBeenCalled();
    expect(logoutMock).not.toHaveBeenCalled();
    expect(pushMock).not.toHaveBeenCalled();
  });
});
