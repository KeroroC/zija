import { defineStore } from "pinia";
import type { SessionInfo, CurrentMember, HouseholdStatus } from "../types/identity";
import { authApi } from "../api/auth";
import { householdApi } from "../api/household";
import { clearCsrf } from "../api/http";

interface SessionState {
  initialized: boolean;
  householdInitialized: boolean | null;
  session: SessionInfo | null;
  currentMember: CurrentMember | null;
}

const PUBLIC_ROUTES = ["login", "bootstrap", "invitation-redeem", "owner-recovery"];

export const useSessionStore = defineStore("session", {
  state: (): SessionState => ({
    initialized: false,
    householdInitialized: null,
    session: null,
    currentMember: null
  }),

  getters: {
    authenticated: (state) => state.session?.authenticated ?? false,
    role: (state) => state.currentMember?.role ?? null
  },

  actions: {
    async ensureInitialized() {
      if (this.initialized) return;
      try {
        const status = await householdApi.getStatus();
        this.householdInitialized = status.initialized;
        if (status.initialized) {
          const session = await authApi.getSession();
          this.session = session;
          if (session.authenticated) {
            this.currentMember = await householdApi.getCurrentMember();
          }
        }
      } catch {
        this.session = null;
      }
      this.initialized = true;
    },

    async login(username: string, password: string) {
      await authApi.initializeCsrf();
      const session = await authApi.login({ username, password });
      this.session = session;
      if (session.authenticated) {
        this.currentMember = await householdApi.getCurrentMember();
      }
    },

    async logout() {
      try {
        await authApi.logout();
      } finally {
        this.session = null;
        this.currentMember = null;
        clearCsrf();
      }
    },

    isPublicRoute(route: { name: unknown }): boolean {
      return typeof route.name === "string"
        && PUBLIC_ROUTES.includes(route.name);
    }
  }
});
