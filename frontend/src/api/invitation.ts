import type { InvitationInfo, InvitationInspect } from "../types/identity";
import { postJson } from "./http";

export const invitationApi = {
  inspect: (token: string) =>
    postJson<InvitationInspect>("/api/v1/invitations/inspect", { token }),
  create: (role: "ADMIN" | "MEMBER", expiresInHours: number) =>
    postJson<InvitationInfo>("/api/v1/invitations", { role, expiresInHours }),
  redeem: (token: string, data: { username: string; password: string; displayName: string; email?: string }) =>
    postJson("/api/v1/invitations/redeem", { token, ...data }),
};
