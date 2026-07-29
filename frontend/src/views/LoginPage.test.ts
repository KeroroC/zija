import { readFileSync } from "node:fs";
import { resolve } from "node:path";
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

  it("exposes password-manager-friendly autocomplete hints so Bitwarden can identify both fields", async () => {
    // Regression: Bitwarden identifies the password field via type="password" even
    // when autocomplete="off", but it cannot identify a text-type username field
    // without autocomplete="username". Without this hint Bitwarden only autofills
    // the password, leaving the username blank. See MDN "Identify Input Purpose"
    // and WCAG 2.2 SC 1.3.5.
    wrapper = mount(LoginPage, { global: { plugins: [ElementPlus] } });
    const inputs = wrapper.findAll("input");

    expect(inputs[0].attributes("autocomplete")).toBe("username");
    expect(inputs[1].attributes("autocomplete")).toBe("current-password");
  });

  it("submits when the user presses Enter in the password field", async () => {
    wrapper = mount(LoginPage, { global: { plugins: [ElementPlus] } });
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("owner");
    await inputs[1].setValue("secret");
    await inputs[1].trigger("keyup.enter");
    await flushPromises();

    expect(loginMock).toHaveBeenCalledWith("owner", "secret");
    expect(pushMock).toHaveBeenCalledWith({ name: "home" });
  });

  it("submits when the user presses Enter in the username field", async () => {
    wrapper = mount(LoginPage, { global: { plugins: [ElementPlus] } });
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("owner");
    await inputs[0].trigger("keyup.enter");
    await flushPromises();

    expect(loginMock).toHaveBeenCalledWith("owner", "");
    expect(pushMock).toHaveBeenCalledWith({ name: "home" });
  });

  it("shows the credentials-mismatch toast when login throws an unknown error", async () => {
    // Lock in the user-facing copy for the most common failure path.
    loginMock.mockRejectedValue({ errorCode: "AUTH_INVALID_CREDENTIALS" });
    wrapper = mount(LoginPage, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] }
    });
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("owner");
    await inputs[1].setValue("wrong");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    const message = document.querySelector(".el-message--error");
    expect(message).toBeTruthy();
    expect(message?.textContent).toContain("用户名或密码错误");
  });

  it("shows the rate-limit toast when the backend reports AUTH_LOGIN_RATE_LIMITED", async () => {
    loginMock.mockRejectedValue({ errorCode: "AUTH_LOGIN_RATE_LIMITED" });
    wrapper = mount(LoginPage, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] }
    });
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("owner");
    await inputs[1].setValue("secret");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    const message = document.querySelector(".el-message--error");
    expect(message).toBeTruthy();
    expect(message?.textContent).toContain("尝试过多");
  });
});

describe("main.ts Element Plus API-only CSS imports", () => {
  // Regression: LoginPage uses ElMessage.error() as a JS-only imperative API.
  // ElementPlusResolver's on-demand CSS scanner only inspects <template>
  // usage, so without an explicit import the .el-message rules (including
  // position: fixed) are stripped from the production bundle and the login
  // error toast renders as an unstyled <div> against the dark auth-stage.
  // The previous logout-dialog fix (af0a1ae) added el-message-box.css +
  // el-overlay.css but missed el-message.css despite the comment calling it
  // out. This test guards against the same omission recurring.
  it("imports el-message.css so ElMessage renders with positioning and theming", () => {
    const mainTs = readFileSync(resolve(__dirname, "../main.ts"), "utf-8");
    expect(mainTs).toMatch(
      /import\s+["']element-plus\/theme-chalk\/el-message\.css["']/
    );
  });
});
