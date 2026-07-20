import ElementPlus from "element-plus";
import { mount } from "@vue/test-utils";
import {
  createMemoryHistory,
  createRouter
} from "vue-router";
import { createPinia, setActivePinia } from "pinia";
import { h } from "vue";
import { beforeEach, describe, expect, it } from "vitest";
import AppShell from "./AppShell.vue";
import { useSessionStore } from "../stores/session";

describe("AppShell", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
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
      status: "ACTIVE"
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

    const wrapper = mount(AppShell, {
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
    wrapper.unmount();
  });

  it("hides authenticated navigation when not signed in", async () => {
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

    const wrapper = mount(AppShell, {
      global: {
        plugins: [router, ElementPlus]
      }
    });

    expect(wrapper.text()).toContain("首页");
    expect(wrapper.text()).not.toContain("成员管理");
    expect(wrapper.text()).not.toContain("个人资料");
    expect(wrapper.text()).not.toContain("登出");
    wrapper.unmount();
  });
});
