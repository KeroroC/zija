export interface AiSettings {
  enabled: boolean;
  providerId: string;
  credentialConfigured: boolean;
  outboundEnabled: boolean;
  requestsPerMinute: number;
  maxContextTokens: number;
  maxConcurrentRequests: number;
  requestTimeoutSeconds: number;
  version: number;
}

export interface AiStatus {
  available: boolean;
  reasonCode: string;
  detail: string;
  providerId: string;
  chatModel: string | null;
  embeddingModel: string | null;
  outboundEnabled: boolean;
  requestsPerMinute: number;
  maxContextTokens: number;
  maxConcurrentRequests: number;
  requestTimeoutSeconds: number;
}

export interface AiSettingsUpdate {
  enabled: boolean;
  providerId: string;
  credential?: string;
  clearCredential: boolean;
  outboundEnabled: boolean;
  requestsPerMinute: number;
  maxContextTokens: number;
  maxConcurrentRequests: number;
  requestTimeoutSeconds: number;
  version: number;
}
