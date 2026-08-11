import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { h } from "vue";
import { afterEach, describe, expect, it } from "vitest";
import ReportsLayout from "../ReportsLayout.vue";

// 与真实路由同构的最小路由表：报表子页全部可达（占位组件，无需 API/数据）
function createTestRouter() {
  const placeholder = () => h("div", "占位页");
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: "/reports",
        component: { render: () => h("div") },
        children: [
          { path: "", redirect: "/reports/search" },
          { path: "search", name: "report-search", component: { render: placeholder } },
          { path: "stock-by-location", name: "report-stock-by-location", component: { render: placeholder } },
          { path: "expiring-lots", name: "report-expiring-lots", component: { render: placeholder } },
          { path: "low-stock", name: "report-low-stock", component: { render: placeholder } },
          { path: "stock-changes", name: "report-stock-changes", component: { render: placeholder } },
          { path: "movements", name: "report-movements", component: { render: placeholder } },
          { path: "settings", name: "report-settings", component: { render: placeholder } }
        ]
      }
    ]
  });
}

describe("ReportsLayout", () => {
  let wrapper: VueWrapper | null = null;

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  async function mountAt(path: string) {
    const router = createTestRouter();
    await router.push(path);
    await router.isReady();
    wrapper = mount(ReportsLayout, {
      global: { plugins: [router, ElementPlus] }
    });
    return router;
  }

  function activeTabText() {
    return wrapper!.find(".el-tabs__item.is-active").text();
  }

  it("renders a tab for every report sub-page", async () => {
    await mountAt("/reports/search");

    const tabs = wrapper!.findAll(".el-tabs__item").map((t) => t.text());
    expect(tabs).toEqual([
      "全局搜索",
      "库存分布",
      "临期批次",
      "低库存",
      "库存变化",
      "流水",
      "报表设置"
    ]);
  });

  it("navigates to the matching route when a tab is clicked", async () => {
    const router = await mountAt("/reports/search");

    const lowStockTab = wrapper!
      .findAll(".el-tabs__item")
      .find((t) => t.text() === "低库存")!;
    await lowStockTab.trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.name).toBe("report-low-stock");
    expect(activeTabText()).toBe("低库存");
  });

  it("marks the tab matching the current deep link as active", async () => {
    // 回归：从侧栏/书签直连子页 URL 时，活动标签须与路由一致
    await mountAt("/reports/low-stock");

    expect(activeTabText()).toBe("低库存");
  });

  it("redirects the bare /reports path to the search tab", async () => {
    const router = await mountAt("/reports");
    await flushPromises();

    expect(router.currentRoute.value.name).toBe("report-search");
    expect(activeTabText()).toBe("全局搜索");
  });
});
