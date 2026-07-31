export interface SystemInfo {
  application: string;
  version: string;
  status: "UP";
  installationId: string;
  databaseTime: string;
}

export interface ApiProblem {
  title?: string;
  detail?: string;
  errorCode?: string;
  requestId?: string;
  fieldErrors?: Record<string, string>;
}
