import type { SystemInfo } from "../types/system";
import { getJson } from "./http";

export function fetchSystemInfo(): Promise<SystemInfo> {
  return getJson<SystemInfo>("/api/v1/system/info");
}
