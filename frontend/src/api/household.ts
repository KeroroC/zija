import type { HouseholdStatus, CurrentMember, BootstrapRequest, SessionInfo } from "../types/identity";
import { getJson, postJson, postJsonAndRefreshCsrf } from "./http";

export const householdApi = {
  getStatus: () => getJson<HouseholdStatus>("/api/v1/household/status"),
  getCurrentMember: () => getJson<CurrentMember>("/api/v1/household/me"),
  bootstrap: (data: BootstrapRequest) =>
    postJsonAndRefreshCsrf<SessionInfo>("/api/v1/household/bootstrap", data),
  transferOwnership: (targetMemberId: string) =>
    postJson("/api/v1/household/transfer-ownership", { targetMemberId }),
};
