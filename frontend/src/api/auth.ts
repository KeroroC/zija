import type { SessionInfo, LoginRequest, ChangePasswordRequest } from "../types/identity";
import {
  getJson,
  postJsonAndRefreshCsrf,
  putJson,
  ensureCsrf,
  clearCsrf
} from "./http";

export const authApi = {
  login: (data: LoginRequest) =>
    postJsonAndRefreshCsrf<SessionInfo>("/api/v1/auth/login", data),
  logout: () => postJsonAndRefreshCsrf<void>("/api/v1/auth/logout"),
  getSession: () => getJson<SessionInfo>("/api/v1/auth/session"),
  initializeCsrf: () => ensureCsrf(),
  changePassword: (data: ChangePasswordRequest) =>
    putJson("/api/v1/auth/password", data),
};

export { ensureCsrf, clearCsrf };
