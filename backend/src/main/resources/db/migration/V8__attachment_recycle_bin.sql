-- 附件回收站：删除进入回收站（deleted_at），保留期满后由清除任务物理删除。
-- 回收站内的附件不占名字；恢复时若目标挂载点已有同名未删除附件则必须改名。

ALTER TABLE stored_file ADD COLUMN deleted_at TIMESTAMPTZ;
COMMENT ON COLUMN stored_file.deleted_at IS '进入回收站的时间；NULL 表示未删除';

-- 名字唯一约束只约束未删除附件（回收站不占名）。
DROP INDEX IF EXISTS uq_stored_file_mount_name;
CREATE UNIQUE INDEX uq_stored_file_mount_name
    ON stored_file (household_id, mount_type, mount_id, name_normalized)
    WHERE mount_type IS NOT NULL AND name_normalized IS NOT NULL AND deleted_at IS NULL;

-- 既有物品封面升级为挂在对应物品上的附件，并保持封面指定。
-- name_normalized 与应用侧口径一致：去首尾空白 + NFKC 规范化 + 小写；
-- 已有规范化名（如后续版本提前写入）不被覆盖。
-- 注意：PostgreSQL 的 normalize() 第二参数必须是未加引号的 NFKC/NFD/NFC/NFKD 关键字。
UPDATE stored_file sf
SET mount_type = 'ITEM',
    mount_id = ci.id,
    name_normalized = COALESCE(sf.name_normalized,
                               lower(normalize(btrim(sf.original_filename), NFKC)))
FROM catalog_item ci
WHERE ci.cover_file_id = sf.id
  AND sf.mount_type IS NULL;

-- 引用计数不再是附件活着的理由（活着 = 记录存在且未过保留期物理删除），删除该列。
ALTER TABLE stored_file DROP COLUMN reference_count;
