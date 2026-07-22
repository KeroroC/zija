export interface AuditLogItem {
  id: string;
  action: string;
  outcome: string;
  actorAccountId: string | null;
  actor: AccountRef | null;
  subjectAccountId: string | null;
  subject: AccountRef | null;
  detail: Record<string, unknown> | null;
  ipAddress: string | null;
  requestId: string | null;
  createdAt: string;
}

export interface AccountRef {
  id: string;
  username: string;
  displayName: string;
}

export interface AuditLogPage {
  items: AuditLogItem[];
  total: number;
  page: number;
  pageSize: number;
}

export const ACTION_LABELS: Record<string, string> = {
  LOGIN_SUCCESS: "登录成功",
  LOGIN_FAILURE: "登录失败",
  LOGOUT: "登出",
  PASSWORD_CHANGED: "修改密码",
  MEMBER_CREATED: "成员加入",
  MEMBER_ROLE_CHANGED: "角色变更",
  MEMBER_STATUS_CHANGED: "状态变更",
  INVITATION_CREATED: "创建邀请",
  INVITATION_REDEEMED: "兑现邀请",
  OWNERSHIP_TRANSFERRED: "转移所有权",
  OWNER_RECOVERY_USED: "所有者恢复",
};

export const ACTION_OPTIONS = Object.entries(ACTION_LABELS).map(([value, label]) => ({
  value,
  label,
}));

export const OUTCOME_OPTIONS = [
  { value: "SUCCESS", label: "成功" },
  { value: "FAILURE", label: "失败" },
];
