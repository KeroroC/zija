import { createRouter, createWebHistory } from "vue-router";
import SystemStatusView from "../views/SystemStatusView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "system-status",
      component: SystemStatusView
    }
  ]
});
