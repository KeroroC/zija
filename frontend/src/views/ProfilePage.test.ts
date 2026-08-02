import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authApi } from "../api/auth";
import { householdApi } from "../api/household";
import ProfilePage from "./ProfilePage.vue";

const pushMock = vi.fn();
const clearLocalSessionMock = vi.fn();
const refreshCurrentMemberMock = vi.fn();

const currentMember = {
  householdId: "h1",
  memberId: "m1",
  accountId: "a1",
  username: "owner",
  displayName: "所有者",
  role: "OWNER" as const,
  status: "ACTIVE" as const,
  householdName: "我的家"
};

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock })
}));

vi.mock("../api/auth", () => ({
  authApi: { changePassword: vi.fn(), updateDisplayName: vi.fn() }
}));

vi.mock("../api/household", () => ({
  householdApi: { getCurrentMember: vi.fn() }
}));

vi.mock("../stores/session", () => ({
  useSessionStore: () => ({
    currentMember,
    role: "OWNER",
    clearLocalSession: clearLocalSessionMock,
    refreshCurrentMember: refreshCurrentMemberMock
  })
}));

const changePasswordMock = vi.mocked(authApi.changePassword);
const updateDisplayNameMock = vi.mocked(authApi.updateDisplayName);
const getCurrentMemberMock = vi.mocked(householdApi.getCurrentMember);

describe("ProfilePage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    pushMock.mockReset();
    clearLocalSessionMock.mockReset();
    refreshCurrentMemberMock.mockReset().mockResolvedValue(currentMember);
    changePasswordMock.mockReset().mockResolvedValue(undefined);
    updateDisplayNameMock.mockReset().mockResolvedValue(undefined);
    getCurrentMemberMock.mockReset().mockResolvedValue(currentMember);
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("shows username, role and editable display name", () => {
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    expect(wrapper.text()).toContain("owner");
    expect(wrapper.text()).toContain("所有者");
    const nameInput = wrapper.find(".name-edit input");
    expect((nameInput.element as HTMLInputElement).value).toBe("所有者");
  });

  it("updates display name and refreshes current member", async () => {
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    await wrapper.find(".name-edit input").setValue("新名字");
    await wrapper.find(".name-edit .el-button").trigger("click");
    await flushPromises();

    expect(updateDisplayNameMock).toHaveBeenCalledWith({ displayName: "新名字" });
    expect(refreshCurrentMemberMock).toHaveBeenCalledOnce();
  });

  it("rejects blank display name without calling the api", async () => {
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    await wrapper.find(".name-edit input").setValue("   ");
    await wrapper.find(".name-edit .el-button").trigger("click");
    await flushPromises();

    expect(updateDisplayNameMock).not.toHaveBeenCalled();
  });

  it("clears only the local session and navigates to login after changing password", async () => {
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    const inputs = wrapper.find("form").findAll("input");
    await inputs[0].setValue("old-secret");
    await inputs[1].setValue("new-secret");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(changePasswordMock).toHaveBeenCalledWith({
      currentPassword: "old-secret",
      newPassword: "new-secret"
    });
    expect(clearLocalSessionMock).toHaveBeenCalledOnce();
    expect(pushMock).toHaveBeenCalledWith({ name: "login" });
  });

  it("keeps the local session and current route when changing password fails", async () => {
    changePasswordMock.mockRejectedValue(new Error("password change failed"));
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    const inputs = wrapper.find("form").findAll("input");
    await inputs[0].setValue("old-secret");
    await inputs[1].setValue("new-secret");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(clearLocalSessionMock).not.toHaveBeenCalled();
    expect(pushMock).not.toHaveBeenCalled();
  });
});
