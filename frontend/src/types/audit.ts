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
  // 身份
  LOGIN_SUCCESS: "登录成功",
  LOGIN_FAILURE: "登录失败",
  LOGOUT: "登出",
  PASSWORD_CHANGED: "修改密码",
  DISPLAY_NAME_CHANGED: "修改显示名称",
  // 成员
  MEMBER_JOINED: "成员加入",
  ROLE_CHANGED: "角色变更",
  MEMBER_DEACTIVATED: "成员停用",
  MEMBER_REACTIVATED: "成员启用",
  // 邀请 & 所有权
  INVITATION_CREATED: "创建邀请",
  INVITATION_REDEEMED: "兑现邀请",
  OWNERSHIP_TRANSFERRED: "转移所有权",
  OWNER_RECOVERY: "所有者恢复",
  // 家庭
  HOUSEHOLD_INITIALIZED: "家庭初始化",
  // 物品
  ITEM_CREATED: "创建物品",
  ITEM_UPDATED: "更新物品",
  ITEM_ARCHIVED: "归档物品",
  ITEM_RESTORED: "恢复物品",
  ITEM_COVER_UPLOADED: "上传封面",
  ITEM_COVER_REMOVED: "移除封面",
  FILE_UPLOADED: "上传附件",
  FILE_RENAMED: "附件改名",
  FILE_MOVED: "附件转移",
  FILE_DELETED: "附件移入回收站",
  FILE_RESTORED: "恢复附件",
  FILE_PURGED: "永久删除附件",
  // 品类
  CATEGORY_CREATED: "创建品类",
  CATEGORY_UPDATED: "更新品类",
  CATEGORY_ARCHIVED: "归档品类",
  CATEGORY_RESTORED: "恢复品类",
  CATEGORY_MOVED: "移动品类",
  // 品牌
  BRAND_CREATED: "创建品牌",
  BRAND_UPDATED: "更新品牌",
  BRAND_ARCHIVED: "归档品牌",
  BRAND_RESTORED: "恢复品牌",
  // 位置
  LOCATION_CREATED: "创建位置",
  LOCATION_RENAMED: "重命名位置",
  LOCATION_MOVED: "移动位置",
  LOCATION_DELETED: "删除位置",
  // 标签
  TAG_CREATED: "创建标签",
  TAG_UPDATED: "更新标签",
  TAG_ARCHIVED: "归档标签",
  TAG_RESTORED: "恢复标签",
  // 单位
  UNIT_CREATED: "创建单位",
  UNIT_UPDATED: "更新单位",
  UNIT_ARCHIVED: "归档单位",
  UNIT_RESTORED: "恢复单位",
  UNIT_DECIMAL_SCALE_UPDATED: "更新单位精度",
  // 库存
  INVENTORY_INBOUND: "入库",
  INVENTORY_CONSUME: "领用",
  INVENTORY_LOSS: "报损",
  INVENTORY_TRANSFER: "调拨",
  INVENTORY_REVERSAL: "撤销流水",
  INVENTORY_STOCKTAKE_CANCEL: "取消盘点",
  INVENTORY_STOCKTAKE_CONFIRM: "确认盘点",
  // 报表
  EXPORT_PERFORMED: "导出报表",
  REPORTING_PROJECTION_REBUILT: "重建报表投影",
  REPORTING_EVENT_ABANDONED: "放弃报表事件",
  // 提醒
  REMINDER_RULE_UPDATE: "更新提醒规则",
  REMINDER_TASK_SNOOZED: "稍后提醒",
  REMINDER_TASK_COMPLETED: "完成任务",
  REMINDER_TASK_IGNORED: "忽略提醒",
  REMINDER_TASK_REOPENED: "重开提醒",
  REMINDER_EVENT_POISON: "提醒事件异常",
  // 邮件
  MAIL_SETTING_UPDATE: "更新邮件设置",
  MAIL_DIGEST_SENT: "发送邮件摘要",
  MAIL_SEND_FAILED: "邮件发送失败",
};

export const ACTION_OPTIONS = Object.entries(ACTION_LABELS).map(([value, label]) => ({
  value,
  label,
}));

export const OUTCOME_OPTIONS = [
  { value: "SUCCESS", label: "成功" },
  { value: "FAILURE", label: "失败" },
];
