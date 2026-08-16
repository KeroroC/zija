-- 冲正唯一约束：一笔流水最多一条 REVERSAL。
-- 背景：ReversalService 的无键冲正路径用非锁定 SELECT COUNT(*) 检查是否已冲正
-- （countReversalOf，存在 TOCTOU 窗口），并发无键调用可能同时看到 count=0
-- 并各插入一条 REVERSAL，造成重复冲正（库存双重回补）。
-- 修复：把 reversal_of 上的普通索引改为 UNIQUE，由数据库层兜底——
-- 第二条并发 INSERT 因 unique violation 回滚，无需依赖应用层锁。
-- PostgreSQL 唯一索引允许多个 NULL，因此不影响 reversal_of 为 NULL 的普通流水。
-- 注意：若历史数据中已存在同一 reversal_of 的多条 REVERSAL（本 bug 已触发过），
-- 本迁移会因唯一冲突而失败并阻止启动——此时需人工先清理重复冲正并复核库存位
-- （用一致性检查核对），不要静默删除，以免掩盖库存位与流水的不一致。
DROP INDEX IF EXISTS idx_inventory_movement_reversal_of;
CREATE UNIQUE INDEX idx_inventory_movement_reversal_of
    ON inventory_movement (reversal_of);
