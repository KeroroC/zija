import { createRouter, createWebHistory } from "vue-router";
import { useSessionStore } from "../stores/session";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", name: "home", component: () => import("../views/HomeView.vue"), meta: { title: "首页" } },
    { path: "/system", name: "system-status", component: () => import("../views/SystemStatusView.vue"), meta: { title: "系统状态" } },
    { path: "/reminders", name: "reminders", component: () => import("../views/RemindersView.vue"), meta: { title: "提醒中心" } },
    { path: "/notifications", name: "notifications", component: () => import("../views/NotificationsView.vue"), meta: { title: "通知" } },
    { path: "/bootstrap", name: "bootstrap", component: () => import("../views/BootstrapPage.vue"), meta: { title: "初始化" } },
    { path: "/login", name: "login", component: () => import("../views/LoginPage.vue"), meta: { title: "登录" } },
    { path: "/invitation/redeem", name: "invitation-redeem", component: () => import("../views/InvitationRedeemPage.vue"), meta: { title: "加入家庭" } },
    { path: "/members", name: "members", component: () => import("../views/MembersPage.vue"), meta: { title: "成员管理" } },
    { path: "/audit-logs", name: "audit-logs", component: () => import("../views/AuditLogPage.vue"), meta: { title: "审计日志" } },
    { path: "/profile", name: "profile", component: () => import("../views/ProfilePage.vue"), meta: { title: "个人资料" } },
    { path: "/owner-recovery", name: "owner-recovery", component: () => import("../views/OwnerRecoveryPage.vue"), meta: { title: "重置密码" } },
    { path: "/items", name: "items", component: () => import("../views/ItemsPage.vue"), meta: { title: "物品资料" } },
    { path: "/files", name: "files", component: () => import("../views/AttachmentsPage.vue"), meta: { title: "附件" } },
    { path: "/locations", name: "locations", component: () => import("../views/LocationsPage.vue"), meta: { title: "位置管理" } },
    { path: "/settings", redirect: "/settings/catalog" },
    { path: "/settings/catalog", name: "catalog-settings", component: () => import("../views/CatalogSettingsPage.vue"), meta: { title: "目录设置" } },
    { path: "/settings/brands", name: "brands-settings", component: () => import("../views/CatalogSettingsPage.vue"), meta: { title: "目录设置" } },
    { path: "/settings/units", name: "units-settings", component: () => import("../views/CatalogSettingsPage.vue"), meta: { title: "目录设置" } },
    { path: "/settings/tags", name: "tags-settings", component: () => import("../views/CatalogSettingsPage.vue"), meta: { title: "目录设置" } },
    { path: "/settings/reminder", name: "reminder-settings", component: () => import("../views/CatalogSettingsPage.vue"), meta: { title: "提醒规则" } },
    { path: "/settings/ai", name: "ai-settings", component: () => import("../views/CatalogSettingsPage.vue"), meta: { title: "AI 能力" } },
    { path: "/inventory", name: "inventory", component: () => import("../views/InventoryPage.vue"), meta: { title: "库存管理" } },
    {
      path: "/reports",
      component: () => import("../views/reports/ReportsLayout.vue"),
      meta: { title: "报表与导出" },
      children: [
        { path: "", redirect: "/reports/search" },
        { path: "search", name: "report-search", component: () => import("../views/reports/SearchView.vue"), meta: { title: "全局搜索" } },
        { path: "stock-by-location", name: "report-stock-by-location", component: () => import("../views/reports/StockByLocationView.vue"), meta: { title: "库存分布" } },
        { path: "expiring-lots", name: "report-expiring-lots", component: () => import("../views/reports/ExpiringLotsView.vue"), meta: { title: "临期批次" } },
        { path: "low-stock", name: "report-low-stock", component: () => import("../views/reports/LowStockView.vue"), meta: { title: "低库存" } },
        { path: "movements", name: "report-movements", component: () => import("../views/reports/MovementsView.vue"), meta: { title: "流水" } },
        { path: "settings", name: "report-settings", component: () => import("../views/reports/ReportsSettingsView.vue"), meta: { title: "报表设置" } }
      ]
    },
    { path: "/:pathMatch(.*)*", name: "not-found", component: () => import("../views/NotFoundPage.vue"), meta: { title: "页面不存在" } }
  ]
});

router.beforeEach(async (to) => {
  const session = useSessionStore();
  await session.ensureInitialized();

  if (!session.householdInitialized && to.name !== "bootstrap") {
    return { name: "bootstrap" };
  }

  if (session.isPublicRoute(to)) {
    return true;
  }

  if (!session.authenticated) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
});

router.afterEach((to) => {
  const title = to.meta.title as string | undefined;
  document.title = title ? `${title} · 知家` : "知家 · zija";
});
