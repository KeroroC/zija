# ADR-003: 审计日志查询功能设计

## 状态

已批准

## 背景

系统已有审计日志写入能力（`audit_log` 表、`AuditService`），但缺少查询和展示功能。
需要增加后端查询 API 和前端页面，让管理员/所有者能查看家庭操作记录。

## 决策

### 1. 访问权限

- **仅 OWNER 和 ADMIN** 可访问审计日志查询接口
- MEMBER 无权访问
- 查询范围：当前家庭的所有成员操作记录（通过 `household_id` 过滤）

### 2. 筛选维度

| 筛选项 | 字段 | 类型 |
|--------|------|------|
| 时间范围 | `created_at` | `from` / `to`（ISO-8601） |
| 操作类型 | `action` | 枚举字符串 |
| 操作人 | `actor_account_id` | UUID |
| 操作结果 | `outcome` | `SUCCESS` / `FAILURE` |

### 3. 分页策略

- 使用 MyBatis-Plus `Page<T>` 分页插件
- 请求参数：`page`（从1开始）、`pageSize`（默认20，最大100）
- 响应包含：`total`、`page`、`pageSize`、`items[]`

### 4. API 设计

```
GET /api/v1/audit-logs
  ?page=1
  &pageSize=20
  &from=2026-07-01T00:00:00Z
  &to=2026-07-22T23:59:59Z
  &action=LOGIN_SUCCESS
  &actorAccountId=uuid
  &outcome=SUCCESS
```

响应：
```json
{
  "items": [
    {
      "id": "uuid",
      "action": "LOGIN_SUCCESS",
      "outcome": "SUCCESS",
      "actorAccountId": "uuid",
      "actor": { "id": "uuid", "username": "zhangsan", "displayName": "张三" },
      "subjectAccountId": null,
      "subject": null,
      "detail": {"username": "zhangsan"},
      "ipAddress": "192.168.1.1",
      "requestId": "uuid",
      "createdAt": "2026-07-22T10:30:00+08:00"
    }
  ],
  "total": 150,
  "page": 1,
  "pageSize": 20
}
```

### 5. 前端展示

- **列表列**：时间、操作类型（中文标签）、操作人、目标成员、结果、IP、关键信息
- **筛选栏**：快捷时间选项（今天/7天/30天）+ 自定义日期范围、操作类型下拉、操作人下拉
- **排序**：固定按时间倒序
- **详情**：列表直接显示 detail 关键字段；点击行弹出详情抽屉显示完整信息
- **导航**：侧边栏独立菜单项「审计日志」，位于「成员管理」之后

### 6. Action 中文映射（前端硬编码）

| Action | 中文 |
|--------|------|
| LOGIN_SUCCESS | 登录成功 |
| LOGIN_FAILURE | 登录失败 |
| LOGOUT | 登出 |
| PASSWORD_CHANGED | 修改密码 |
| MEMBER_CREATED | 成员加入 |
| MEMBER_ROLE_CHANGED | 角色变更 |
| MEMBER_STATUS_CHANGED | 状态变更 |
| INVITATION_CREATED | 创建邀请 |
| INVITATION_REDEEMED | 兑现邀请 |
| OWNERSHIP_TRANSFERRED | 转移所有权 |
| OWNER_RECOVERY_USED | 所有者恢复 |

### 7. 模块边界约束

- `system` 模块不能依赖 `identity` 或 `household`（会产生循环依赖）
- 审计日志查询端点放在 `household` 模块的 `AuditLogController` 中，因为该模块已依赖 `identity` 和 `system`
- `SystemApi` 新增 `queryAuditLogs()` 方法作为跨模块查询契约
- `AuditLogController` 使用 `@RequireAdmin` 注解进行权限控制
- 前端通过 `actor`/`subject` 嵌套对象获取操作人和目标成员的显示名称

## 后果

- 管理员/所有者可以追溯家庭内的所有敏感操作
- 为后续安全审计、问题排查提供数据支撑
- 数据保留策略后续再考虑（暂不实现自动清理）
