import { defineStore } from "pinia";
import type { SessionInfo, CurrentMember } from "../types/identity";
import { authApi } from "../api/auth";
import { householdApi } from "../api/household";
import { clearCsrf } from "../api/http";

interface SessionState {
  initialized: boolean;
  householdInitialized: boolean | null;
  session: SessionInfo | null;
  currentMember: CurrentMember | null;
  sessionEpoch: number;
}

const PUBLIC_ROUTES = ["login", "bootstrap", "invitation-redeem", "owner-recovery"];

export const useSessionStore = defineStore("session", {
  state: (): SessionState => ({
    initialized: false,
    householdInitialized: null,
    session: null,
    currentMember: null,
    sessionEpoch: 0
  }),

  getters: {
    authenticated: (state) => state.session?.authenticated ?? false,
    role: (state) => state.currentMember?.role ?? null
  },

  actions: {
    async applySession(session: SessionInfo): Promise<boolean> {
      const epoch = ++this.sessionEpoch;
      return await this.applySessionAtEpoch(session, epoch);
    },

    async applySessionAtEpoch(session: SessionInfo, epoch: number): Promise<boolean> {
      if (epoch !== this.sessionEpoch) return false;
      if (session.authenticated) {
        try {
          const currentMember = await householdApi.getCurrentMember();
          if (epoch !== this.sessionEpoch) return false;
          this.session = session;
          this.currentMember = currentMember;
          this.initialized = true;
          return true;
        } catch {
          if (epoch !== this.sessionEpoch) return false;
          this.session = null;
          this.currentMember = null;
          this.initialized = false;
          return false;
        }
      }
      this.session = session;
      this.currentMember = null;
      this.initialized = true;
      return true;
    },

    async ensureInitialized(): Promise<boolean> {
      if (this.initialized) return true;
      const epoch = ++this.sessionEpoch;
      try {
        const status = await householdApi.getStatus();
        if (epoch !== this.sessionEpoch) return false;
        this.householdInitialized = status.initialized;
        if (!status.initialized) {
          this.session = null;
          this.currentMember = null;
          this.initialized = true;
          return true;
        }
        const session = await authApi.getSession();
        return await this.applySessionAtEpoch(session, epoch);
      } catch {
        if (epoch !== this.sessionEpoch) return false;
        this.session = null;
        this.currentMember = null;
        this.initialized = false;
        return false;
      }
    },

    async login(username: string, password: string): Promise<boolean> {
      const epoch = ++this.sessionEpoch;
      await authApi.initializeCsrf();
      const session = await authApi.login({ username, password });
      return await this.applySessionAtEpoch(session, epoch);
    },

    async logout() {
      ++this.sessionEpoch;
      try {
        await authApi.logout();
      } catch (error) {
        clearCsrf();
        throw error;
      }
      this.session = null;
      this.currentMember = null;
    },

    clearLocalSession() {
      ++this.sessionEpoch;
      this.session = null;
      this.currentMember = null;
      this.initialized = true;
      clearCsrf();
    },

    isPublicRoute(route: { name: unknown }): boolean {
      return typeof route.name === "string"
        && PUBLIC_ROUTES.includes(route.name);
    }
  }
});
