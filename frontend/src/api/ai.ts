import { getJson, putJson } from "./http";
import type { AiSettings, AiSettingsUpdate, AiStatus } from "../types/ai";

export type { AiSettings, AiSettingsUpdate, AiStatus } from "../types/ai";

export function fetchAiSettings(): Promise<AiSettings> {
  return getJson<AiSettings>("/api/v1/ai/settings");
}

export function fetchAiStatus(): Promise<AiStatus> {
  return getJson<AiStatus>("/api/v1/ai/status");
}

export function updateAiSettings(body: AiSettingsUpdate): Promise<AiSettings> {
  return putJson<AiSettings>("/api/v1/ai/settings", body);
}
