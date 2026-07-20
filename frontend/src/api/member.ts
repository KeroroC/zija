import type { MemberInfo } from "../types/identity";
import { getJson, putJson } from "./http";

export const memberApi = {
  list: () => getJson<MemberInfo[]>("/api/v1/members"),
  updateRole: (id: string, role: "ADMIN" | "MEMBER") =>
    putJson(`/api/v1/members/${id}/role`, { role }),
  updateStatus: (id: string, status: "ACTIVE" | "DEACTIVATED") =>
    putJson(`/api/v1/members/${id}/status`, { status }),
};
