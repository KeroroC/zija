import type { AuditLogPage } from "../types/audit";
import { getJson } from "./http";

export interface AuditLogQueryParams {
  page?: number;
  pageSize?: number;
  from?: string;
  to?: string;
  action?: string;
  actorAccountId?: string;
  outcome?: string;
}

export function fetchAuditLogs(params: AuditLogQueryParams = {}): Promise<AuditLogPage> {
  const searchParams = new URLSearchParams();
  if (params.page) searchParams.set("page", String(params.page));
  if (params.pageSize) searchParams.set("pageSize", String(params.pageSize));
  if (params.from) searchParams.set("from", params.from);
  if (params.to) searchParams.set("to", params.to);
  if (params.action) searchParams.set("action", params.action);
  if (params.actorAccountId) searchParams.set("actorAccountId", params.actorAccountId);
  if (params.outcome) searchParams.set("outcome", params.outcome);

  const qs = searchParams.toString();
  return getJson<AuditLogPage>(`/api/v1/audit-logs${qs ? "?" + qs : ""}`);
}
