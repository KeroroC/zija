import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import {
  createMemoryHistory,
  createRouter
} from "vue-router";
import { createPinia, setActivePinia } from "pinia";
import { h } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AppShell from "./AppShell.vue";
import { useSessionStore } from "../stores/session";

describe("AppShell", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  async function triggerUserCommand(command: string) {
    const userDropdown = wrapper!
      .findAllComponents({ name: "ElDropdown" })
      .find((c) => c.find(".user-trigger").exists());
    expect(userDropdown).toBeDefined();
    await userDropdown!.vm.$emit("command", command);
    await flushPromises();
  }

  async function triggerInventoryCommand(command: string) {
    const inventoryDropdown = wrapper!
      .findAllComponents({ name: "ElDropdown" })
      .find((c) => c.find(".el-button").text().includes("库存操作"));
    expect(inventoryDropdown).toBeDefined();
    await inventoryDropdown!.vm.$emit("command", command);
    await flushPromises();
  }

  it("renders the approved desktop navigation when authenticated", async () => {
    const session = useSessionStore();
    session.session = {
      authenticated: true,
      accountId: "a1",
      username: "admin",
      displayName: "Admin"
    };
    session.currentMember = {
      householdId: "h1",
      memberId: "m1",
      accountId: "a1",
      username: "admin",
      displayName: "Admin",
      role: "ADMIN",
      status: "ACTIVE",
      householdName: "测试家庭"
    };

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: "/",
          component: { render: () => h("div", "系统状态") }
        }
      ]
    });
    await router.push("/");
    await router.isReady();

    wrapper = mount(AppShell, {
      global: {
        plugins: [router, ElementPlus]
      }
    });

    expect(wrapper.text()).toContain("知家");
    expect(wrapper.text()).toContain("首页");
    expect(wrapper.text()).toContain("成员管理");
    expect(wrapper.text()).toContain("个人资料");
    expect(wrapper.text()).toContain("物品资料");
    expect(wrapper.text()).toContain("库存管理");
    expect(wrapper.text()).toContain("位置管理");
    expect(wrapper.text()).toContain("提醒中心");
    expect(wrapper.text()).toContain("报表与导出");
    expect(wrapper.text()).toContain("家庭设置");
    // 顶栏显示当前成员显示名（不再显示角色徽章）
    const userTrigger = wrapper.find(".user-trigger");
    expect(userTrigger.exists()).toBe(true);
    expect(userTrigger.text()).toContain("Admin");
    // 角色徽章已移除，登出收纳在下拉菜单中（未展开不渲染）
    expect(wrapper.text()).not.toContain("管理员");
    expect(wrapper.text()).not.toContain("登出");
    expect(wrapper.text()).toContain("库存操作");

    // inventory menu item should be enabled (not disabled)
    const inventoryItem = wrapper.findAll(".el-menu-item").find(item => item.text().includes("库存管理"));
    expect(inventoryItem).toBeDefined();
    expect(inventoryItem!.classes()).not.toContain("is-disabled");
  });

  it("shows the initialized household name right after login, without requiring a page refresh", async () => {
    const session = useSessionStore();
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/", name: "home", component: { render: () => h("div", "系统状态") } },
        { path: "/login", name: "login", component: { render: () => h("div", "登录页") } }
      ]
    });
    await router.push("/");
    await router.isReady();

    // AppShell mounts at app startup, before login. Its one-shot household-name
    // lookup fails (unauthenticated) and silently falls back to the default.
    wrapper = mount(AppShell, {
      global: { plugins: [router, ElementPlus] }
    });
    await flushPromises();

    // Simulate a successful login: the session store now carries the authenticated
    // session plus the current member, including the real household name.
    session.session = { authenticated: true, accountId: "a1", username: "admin", displayName: "Admin" };
    session.currentMember = {
      householdId: "h1",
      memberId: "m1",
      accountId: "a1",
      username: "admin",
      displayName: "Admin",
      role: "ADMIN",
      status: "ACTIVE",
      householdName: "测试家庭"
    };
    await router.push({ name: "home" });
    await flushPromises();

    expect(wrapper.text()).toContain("测试家庭");
    expect(wrapper.text()).not.toContain("家庭：我的家");
  });

  it("hides sidebar completely when not signed in", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: "/",
          component: { render: () => h("div", "系统状态") }
        }
      ]
    });
    await router.push("/");
    await router.isReady();

    wrapper = mount(AppShell, {
      global: {
        plugins: [router, ElementPlus]
      }
    });

    expect(wrapper.text()).toContain("系统状态");
    expect(wrapper.text()).not.toContain("知家");
    expect(wrapper.text()).not.toContain("首页");
    expect(wrapper.text()).not.toContain("成员管理");
    expect(wrapper.text()).not.toContain("个人资料");
    expect(wrapper.text()).not.toContain("登出");
  });

  it("hides the shell when authenticated but the current route is a public full-screen route", async () => {
    const session = useSessionStore();
    session.session = { authenticated: true, accountId: "a1", username: "admin", displayName: "Admin" };
    session.currentMember = {
      householdId: "h1",
      memberId: "m1",
      accountId: "a1",
      username: "admin",
      displayName: "Admin",
      role: "ADMIN",
      status: "ACTIVE",
      householdName: "测试家庭"
    };
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/login", name: "login", component: { render: () => h("div", "登录页") } },
        { path: "/invitation/redeem", name: "invitation-redeem", component: { render: () => h("div", "邀请页") } }
      ]
    });
    await router.push("/login");
    await router.isReady();
    wrapper = mount(AppShell, {
      global: {
        plugins: [router, ElementPlus]
      }
    });

    expect(wrapper.text()).toContain("登录页");
    expect(wrapper.text()).not.toContain("知家");
    expect(wrapper.text()).not.toContain("成员管理");
    expect(wrapper.text()).not.toContain("登出");

    await router.push("/invitation/redeem");
    await flushPromises();
    expect(wrapper.text()).toContain("邀请页");
    expect(wrapper.text()).not.toContain("知家");
    expect(wrapper.text()).not.toContain("成员管理");
  });

  it("shows an error and stays on the current route when logout fails", async () => {
    const session = useSessionStore();
    session.session = { authenticated: true, username: "admin" };
    session.currentMember = {
      householdId: "h1",
      memberId: "m1",
      accountId: "a1",
      username: "admin",
      displayName: "Admin",
      role: "ADMIN",
      status: "ACTIVE",
      householdName: "测试家庭"
    };
    const logoutSpy = vi.spyOn(session, "logout").mockRejectedValue(new Error("logout unavailable"));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/", name: "home", component: { render: () => h("div", "系统状态") } },
        { path: "/login", name: "login", component: { render: () => h("div", "登录") } }
      ]
    });
    await router.push("/");
    await router.isReady();
    wrapper = mount(AppShell, {
      attachTo: document.body,
      global: { plugins: [router, ElementPlus] }
    });

    await triggerUserCommand("logout");

    const messageBox = document.querySelector(".el-message-box");
    expect(messageBox).toBeTruthy();
    const confirmBtn = messageBox!.querySelector(".el-button--primary") as HTMLButtonElement;
    confirmBtn.click();
    await flushPromises();
    expect(logoutSpy).toHaveBeenCalledTimes(1);
    expect(router.currentRoute.value.name).toBe("home");
    expect(document.body.textContent).toContain("登出失败，请重试");
  });

  it("renders the logout confirmation inside the .el-overlay wrapper that provides positioning", async () => {
    // Regression: ElMessageBox.confirm() relies on .el-overlay (position: fixed) and
    // .el-overlay-message-box (centering) styles from element-plus. With on-demand CSS
    // loading, those styles must be imported explicitly because the API call has no
    // <el-message-box> template usage to trigger auto-import. This test locks in the
    // DOM contract those CSS files target — if the overlay wrapper is missing, the
    // dialog falls back to normal document flow and renders at the bottom of the page.
    const session = useSessionStore();
    session.session = { authenticated: true, username: "admin" };
    session.currentMember = {
      householdId: "h1",
      memberId: "m1",
      accountId: "a1",
      username: "admin",
      displayName: "Admin",
      role: "ADMIN",
      status: "ACTIVE",
      householdName: "测试家庭"
    };
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/", name: "home", component: { render: () => h("div", "系统状态") } }]
    });
    await router.push("/");
    await router.isReady();
    wrapper = mount(AppShell, {
      attachTo: document.body,
      global: { plugins: [router, ElementPlus] }
    });

    await triggerUserCommand("logout");

    const overlay = document.querySelector(".el-overlay.is-message-box");
    expect(overlay).toBeTruthy();
    const centeringWrapper = overlay!.querySelector(".el-overlay-message-box");
    expect(centeringWrapper).toBeTruthy();
    expect(centeringWrapper!.querySelector(".el-message-box")).toBeTruthy();
  });

  it("does not log out when the user cancels the confirmation dialog", async () => {
    const session = useSessionStore();
    session.session = { authenticated: true, username: "admin" };
    session.currentMember = {
      householdId: "h1",
      memberId: "m1",
      accountId: "a1",
      username: "admin",
      displayName: "Admin",
      role: "ADMIN",
      status: "ACTIVE",
      householdName: "测试家庭"
    };
    const logoutSpy = vi.spyOn(session, "logout").mockResolvedValue();
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/", name: "home", component: { render: () => h("div", "系统状态") } },
        { path: "/login", name: "login", component: { render: () => h("div", "登录") } }
      ]
    });
    await router.push("/");
    await router.isReady();
    wrapper = mount(AppShell, {
      attachTo: document.body,
      global: { plugins: [router, ElementPlus] }
    });

    await triggerUserCommand("logout");

    const messageBox = document.querySelector(".el-message-box");
    expect(messageBox).toBeTruthy();
    const cancelBtn = Array.from(messageBox!.querySelectorAll(".el-button"))
      .find(b => b.textContent?.includes("取消")) as HTMLButtonElement;
    cancelBtn.click();
    await flushPromises();

    expect(logoutSpy).not.toHaveBeenCalled();
    expect(router.currentRoute.value.name).toBe("home");
  });

  it("navigates to profile when the user dropdown profile command fires", async () => {
    const session = useSessionStore();
    session.session = { authenticated: true, username: "admin" };
    session.currentMember = {
      householdId: "h1", memberId: "m1", accountId: "a1",
      username: "admin", displayName: "Admin", role: "ADMIN",
      status: "ACTIVE", householdName: "测试家庭"
    };
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/", name: "home", component: { render: () => h("div", "系统状态") } },
        { path: "/profile", name: "profile", component: { render: () => h("div", "个人资料页") } }
      ]
    });
    await router.push("/");
    await router.isReady();
    wrapper = mount(AppShell, {
      global: { plugins: [router, ElementPlus] }
    });

    await triggerUserCommand("profile");
    expect(router.currentRoute.value.name).toBe("profile");
  });

  it("navigates to inventory with the action query when an inventory command fires", async () => {
    const session = useSessionStore();
    session.session = { authenticated: true, accountId: "a1", username: "admin", displayName: "Admin" };
    session.currentMember = {
      householdId: "h1",
      memberId: "m1",
      accountId: "a1",
      username: "admin",
      displayName: "Admin",
      role: "ADMIN",
      status: "ACTIVE",
      householdName: "测试家庭"
    };
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/", name: "home", component: { render: () => h("div", "首页") } },
        { path: "/inventory", name: "inventory", component: { render: () => h("div", "库存管理") } }
      ]
    });
    await router.push("/");
    await router.isReady();
    wrapper = mount(AppShell, {
      global: { plugins: [router, ElementPlus] }
    });

    await triggerInventoryCommand("consume");

    expect(router.currentRoute.value.name).toBe("inventory");
    expect(router.currentRoute.value.query.action).toBe("consume");
  });
});
