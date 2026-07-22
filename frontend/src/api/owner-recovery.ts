import type { OwnerRecoveryInspect, OwnerRecoveryResetRequest } from "../types/identity";
import { postJson, postJsonAndRefreshCsrf } from "./http";

export const ownerRecoveryApi = {
  inspect: (token: string) =>
    postJson<OwnerRecoveryInspect>("/api/v1/owner-recovery/inspect", { token }),
  resetPassword: (data: OwnerRecoveryResetRequest) =>
    postJsonAndRefreshCsrf<void>("/api/v1/owner-recovery/reset-password", data),
};
