import { createRouter, createWebHistory } from "vue-router";
import { useSessionStore } from "../stores/session";
import HomeView from "../views/HomeView.vue";
import SystemStatusView from "../views/SystemStatusView.vue";
import RemindersView from "../views/RemindersView.vue";
import NotificationsView from "../views/NotificationsView.vue";
import ReminderRulesSettingsView from "../views/ReminderRulesSettingsView.vue";
import BootstrapPage from "../views/BootstrapPage.vue";
import LoginPage from "../views/LoginPage.vue";
import InvitationRedeemPage from "../views/InvitationRedeemPage.vue";
import MembersPage from "../views/MembersPage.vue";
import ProfilePage from "../views/ProfilePage.vue";
import OwnerRecoveryPage from "../views/OwnerRecoveryPage.vue";
import AuditLogPage from "../views/AuditLogPage.vue";
import ItemsPage from "../views/ItemsPage.vue";
import LocationsPage from "../views/LocationsPage.vue";
import CatalogSettingsPage from "../views/CatalogSettingsPage.vue";
import InventoryPage from "../views/InventoryPage.vue";
import NotFoundPage from "../views/NotFoundPage.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", name: "home", component: HomeView, meta: { title: "首页" } },
    { path: "/system", name: "system-status", component: SystemStatusView, meta: { title: "系统状态" } },
    { path: "/reminders", name: "reminders", component: RemindersView, meta: { title: "提醒中心" } },
    { path: "/notifications", name: "notifications", component: NotificationsView, meta: { title: "通知" } },
    { path: "/settings/reminder", name: "reminder-settings", component: ReminderRulesSettingsView, meta: { title: "提醒规则" } },
    { path: "/bootstrap", name: "bootstrap", component: BootstrapPage, meta: { title: "初始化" } },
    { path: "/login", name: "login", component: LoginPage, meta: { title: "登录" } },
    { path: "/invitation/redeem", name: "invitation-redeem", component: InvitationRedeemPage, meta: { title: "加入家庭" } },
    { path: "/members", name: "members", component: MembersPage, meta: { title: "成员管理" } },
    { path: "/audit-logs", name: "audit-logs", component: AuditLogPage, meta: { title: "审计日志" } },
    { path: "/profile", name: "profile", component: ProfilePage, meta: { title: "个人资料" } },
    { path: "/owner-recovery", name: "owner-recovery", component: OwnerRecoveryPage, meta: { title: "重置密码" } },
    { path: "/items", name: "items", component: ItemsPage, meta: { title: "物品资料" } },
    { path: "/locations", name: "locations", component: LocationsPage, meta: { title: "位置管理" } },
    { path: "/settings/catalog", name: "catalog-settings", component: CatalogSettingsPage, meta: { title: "目录设置" } },
    { path: "/inventory", name: "inventory", component: InventoryPage, meta: { title: "库存管理" } },
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
        { path: "stock-changes", name: "report-stock-changes", component: () => import("../views/reports/StockChangesView.vue"), meta: { title: "库存变化" } },
        { path: "movements", name: "report-movements", component: () => import("../views/reports/MovementsView.vue"), meta: { title: "流水" } },
        { path: "settings", name: "report-settings", component: () => import("../views/reports/ReportsSettingsView.vue"), meta: { title: "报表设置" } }
      ]
    },
    { path: "/:pathMatch(.*)*", name: "not-found", component: NotFoundPage, meta: { title: "页面不存在" } }
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
