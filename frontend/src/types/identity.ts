export interface SessionInfo {
  authenticated: boolean;
  accountId?: string;
  username?: string;
  displayName?: string;
}

export interface HouseholdStatus {
  initialized: boolean;
}

export interface CurrentMember {
  householdId: string;
  memberId: string;
  accountId: string;
  username: string;
  displayName: string;
  role: "OWNER" | "ADMIN" | "MEMBER";
  status: "ACTIVE" | "DEACTIVATED";
}

export interface MemberInfo {
  id: string;
  accountId: string;
  username: string;
  displayName: string;
  role: "OWNER" | "ADMIN" | "MEMBER";
  status: "ACTIVE" | "DEACTIVATED";
}

export interface InvitationInfo {
  id: string;
  token: string;
  role: "ADMIN" | "MEMBER";
  expiresAt: string;
  path: string;
}

export interface InvitationInspect {
  householdName?: string;
  role?: string;
  expiresAt?: string;
  valid: boolean;
}

export interface BootstrapRequest {
  householdName: string;
  username: string;
  password: string;
  displayName: string;
  email?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
