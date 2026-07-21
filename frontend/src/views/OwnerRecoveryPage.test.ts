import ElementPlus from "element-plus";
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { authApi } from "../api/auth";
import { ownerRecoveryApi } from "../api/owner-recovery";
import OwnerRecoveryPage from "./OwnerRecoveryPage.vue";

const pushMock = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock })
}));

vi.mock("../api/auth", () => ({
  authApi: {
    initializeCsrf: vi.fn()
  }
}));

vi.mock("../api/owner-recovery", () => ({
  ownerRecoveryApi: {
    inspect: vi.fn(),
    resetPassword: vi.fn()
  }
}));

const initializeCsrfMock = vi.mocked(authApi.initializeCsrf);
const inspectMock = vi.mocked(ownerRecoveryApi.inspect);
const resetPasswordMock = vi.mocked(ownerRecoveryApi.resetPassword);

describe("OwnerRecoveryPage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    initializeCsrfMock.mockReset().mockResolvedValue(undefined);
    inspectMock.mockReset().mockResolvedValue({ valid: true, ownerDisplayName: "户主" });
    resetPasswordMock.mockReset().mockResolvedValue(undefined);
    window.history.replaceState(null, "", "/owner-recovery#token=recovery%20token");
  });

  it("clears the recovery token and redirects to login after a successful reset", async () => {
    const wrapper = mount(OwnerRecoveryPage, {
      global: {
        plugins: [ElementPlus]
      }
    });
    await flushPromises();

    expect(window.location.pathname).toBe("/owner-recovery");
    expect(window.location.hash).toBe("");
    expect(initializeCsrfMock).toHaveBeenCalledOnce();
    expect(inspectMock).toHaveBeenCalledWith("recovery token");
    expect(initializeCsrfMock.mock.invocationCallOrder[0])
      .toBeLessThan(inspectMock.mock.invocationCallOrder[0]);

    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("new-password");
    await inputs[1].setValue("new-password");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(resetPasswordMock).toHaveBeenCalledWith({
      token: "recovery token",
      newPassword: "new-password"
    });
    expect(pushMock).toHaveBeenCalledWith({ name: "login" });
    wrapper.unmount();
  });

  it("does not submit when the password confirmation differs", async () => {
    const wrapper = mount(OwnerRecoveryPage, {
      global: {
        plugins: [ElementPlus]
      }
    });
    await flushPromises();

    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("new-password");
    await inputs[1].setValue("different-password");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(resetPasswordMock).not.toHaveBeenCalled();
    expect(pushMock).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
