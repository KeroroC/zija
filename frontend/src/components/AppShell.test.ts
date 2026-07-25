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
    expect(wrapper.text()).toContain("管理员");
    expect(wrapper.text()).toContain("登出");
    expect(wrapper.text()).toContain("库存操作");

    // inventory menu item should be enabled (not disabled)
    const inventoryItem = wrapper.findAll(".el-menu-item").find(item => item.text().includes("库存管理"));
    expect(inventoryItem).toBeDefined();
    expect(inventoryItem!.classes()).not.toContain("is-disabled");
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
    vi.spyOn(session, "logout").mockRejectedValue(new Error("logout unavailable"));
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

    const buttons = wrapper.findAll("button");
    const logoutButton = buttons.find(b => b.text().includes("登出"))!;
    await logoutButton.trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.name).toBe("home");
    expect(document.body.textContent).toContain("登出失败，请重试");
  });
});
