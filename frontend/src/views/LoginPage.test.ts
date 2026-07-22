import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import LoginPage from "./LoginPage.vue";

const pushMock = vi.fn();
const loginMock = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ query: {} })
}));

vi.mock("../stores/session", () => ({
  useSessionStore: () => ({ login: loginMock })
}));

describe("LoginPage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    pushMock.mockReset();
    loginMock.mockReset().mockResolvedValue(true);
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("navigates after a complete session synchronization", async () => {
    wrapper = mount(LoginPage, { global: { plugins: [ElementPlus] } });
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("owner");
    await inputs[1].setValue("secret");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(pushMock).toHaveBeenCalledWith({ name: "home" });
  });

  it("navigates so the router can retry session synchronization when it is incomplete", async () => {
    loginMock.mockResolvedValue(false);
    wrapper = mount(LoginPage, { global: { plugins: [ElementPlus] } });
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("owner");
    await inputs[1].setValue("secret");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(pushMock).toHaveBeenCalledWith({ name: "home" });
  });
});
