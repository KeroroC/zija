import { createRouter, createWebHistory } from "vue-router";
import { useSessionStore } from "../stores/session";
import SystemStatusView from "../views/SystemStatusView.vue";
import BootstrapPage from "../views/BootstrapPage.vue";
import LoginPage from "../views/LoginPage.vue";
import InvitationRedeemPage from "../views/InvitationRedeemPage.vue";
import MembersPage from "../views/MembersPage.vue";
import ProfilePage from "../views/ProfilePage.vue";
import OwnerRecoveryPage from "../views/OwnerRecoveryPage.vue";
import AuditLogPage from "../views/AuditLogPage.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", name: "home", component: SystemStatusView },
    { path: "/bootstrap", name: "bootstrap", component: BootstrapPage },
    { path: "/login", name: "login", component: LoginPage },
    { path: "/invitation/redeem", name: "invitation-redeem", component: InvitationRedeemPage },
    { path: "/members", name: "members", component: MembersPage },
    { path: "/audit-logs", name: "audit-logs", component: AuditLogPage },
    { path: "/profile", name: "profile", component: ProfilePage },
    { path: "/owner-recovery", name: "owner-recovery", component: OwnerRecoveryPage }
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
