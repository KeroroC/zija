# 附件由 file 模块持有；封面是物品上的指定

物品封面不再是 file 模块唯一的业务含义。附件（名字、挂载点、回收站）是家庭保管文档的聚合，住在 `file` 模块；物品只保留「哪一份附件是封面」。这修正 ADR-011 的依赖图，并修正 ADR-015 §5「引用计数归零即物理删除」。

## 状态

已批准。修正 [ADR-011](011-modular-monolith-and-module-boundaries.md)、[ADR-015](015-dictionary-archive-and-location-reference.md) §5。

## 决策

- **`file` 从封面 blob 仓库变成附件的家。** 一份附件自己携带挂载点（家庭 / 物品 / 批次，任一时刻恰好一处，可改挂）、可改的名字、回收站状态。不新建附件模块；物品、批次、家庭也不各自持有附件列表。
- **封面是 catalog 上的指定**，不是第二种文件。`catalog_item` 仍只存附件 UUID（延续无跨模块 SQL 外键）。指定不 `retain`；删除封面附件只取消指定，附件进回收站；恢复后是普通附件。
- **`file` 不依赖 `catalog` / `inventory`，避免与已有 `catalog → file` 成环。** 挂载点是同家庭 UUID，file 不校验物品/批次是否存在。改挂与挂到某处的校验由目标所在模块的入口做：挂到物品走 `catalog`，挂到批次走 `inventory`，挂到家庭走 `file`。`inventory` 新增依赖 `file`（批次侧读列表、改挂）。`household` 不依赖 `file`：家庭附件由 file 自有 HTTP 按当前家庭列出。
- **删除进入回收站，保留期满后才物理删除**（保留期可配置，默认三十天）。引用计数不再是附件活着的理由；未过保留期的附件记录本身就是活着的理由。备份文件卷包含回收站内尚未物理删除的对象。
- **合格封面仅 JPEG / PNG / WebP。** 附件另允许 HEIC、PDF、Markdown、TXT、Word / PowerPoint / Excel（含旧版 `.doc` / `.ppt` / `.xls`）；不允许压缩包。图片单件 5 MiB，文档单件 20 MiB。

## 考虑过的备选

- **新建 attachment 模块，file 继续只存字节：** blob 与业务更干净，但多一条边界和一次跨模块编排。知家的「文件」本来就是家庭在保管文档，回收站和挂载点就是这份能力的生命周期。
- **物品 / 批次 / 家庭各自持有附件列表：** 改挂要跨主人搬家，与「附件自己携带挂载点」矛盾。
- **file 调用 CatalogApi / InventoryApi 校验挂载目标：** 与 `catalog → file` 成环，`ModularityTests` 会失败。
- **引用计数归零立即删对象（ADR-015 §5）：** 误删说明书无法反悔。回收站用保留期换磁盘占用。

## 后果

- `inventory` 的 `allowedDependencies` 增加 `file`。定时物理清除必须做成可直接调用的方法（测试里 cron 仍为 `-`，见既有调度约束）。
- 现有 `cover_file_id` 上传/替换/release 流程要改成「新增或指定附件」，不能再把旧封面当作无引用垃圾立刻清掉。
