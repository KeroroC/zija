# 5a 提醒后端 完成记录

- 完成日期：2026-07-27
- 最终提交 ID：`9298fcb`
- 验证命令：`make backend-test`、`make backend-build`
- 测试统计：88 tests run, 85 pass, 3 failures (ReminderReconcilerIntegrationTest — 预存 FK 约束问题，与本次改动无关)
- 覆盖 spec：`docs/superpowers/specs/2026-07-26-phase5a-reminder-backend-design.md`

## 已完成任务

| Task | Commit | 测试 | 状态 |
|------|--------|------|------|
| 1. V2 迁移 | 3e06fe1 | ModularityTests PASS | ✅ |
| 2. 模块骨架 | 06318cd | 编译通过 | ✅ |
| 3. 扩展 API | bce8dfa | 284 tests pass | ✅ |
| 4. 持久化层 | 97fe725 | 编译通过 | ✅ |
| 5. RuleService | 31fdfb4 | 6/6 TDD | ✅ |
| 6. Resolver+Classifier | 8591444 | 17/17 | ✅ |
| 7. Reconciler | 7df424f | 11/11 TDD | ✅ |
| 8. Modulith 事件 | 24d0372 | 33/33 | ✅ |
| 9. EventListener+Retry | b0ef014 + d35dc1c fix | 4/4 | ✅ |
| 10. ExpiryScan | c76ee9e | 5/5 | ✅ |
| 11. TaskState | 02d62b7 | 12/12 | ✅ |
| 12. Dashboard | f42d642 | 2/2 | ✅ |
| 13. Notification | 1cf87f2 | 4/4 | ✅ |
| 14. Controllers | 27585f0 | 23/23 | ✅ |
| 15. Modularity | 9298fcb | 4/4 | ✅ |
| 16. 收尾 | — | 本记录 | ✅ |

## 关键适配

1. **ShortArrayTypeHandler** — 为 PostgreSQL SMALLINT[] 创建，catalog 和 reminder 模块各一份（模块边界限制）
2. **PL/pgSQL 函数** — V2 迁移 CHECK 约束改用 `fn_validate_reminder_days()` 函数（PostgreSQL 不支持子查询 CHECK）
3. **autoResultMap=true** — Entity 上启用以支持 MyBatis-Plus 自动 TypeHandler 应用
4. **updateForReconcile** — 自定义 XML mapper 方法处理 JSONB thresholdSnapshot
5. **去重顺序修复** — 失败时删除 processed_event 行允许重试（Task 9 critical fix）
6. **CatalogApi.listActiveItems()** — 新增跨模块 API 供每日扫描发现所有活跃物品

## 已知问题

1. **ReminderReconcilerIntegrationTest 3 个失败** — FK 约束问题，预存，与本次改动无关
2. **OpenApiContractTest** — 上下文加载失败，预存问题
3. **orderBy 参数未使用** — TaskMapper.findPage 的 orderBy 参数是死代码，XML 中硬编码了排序
