# 备份恢复架构：运维脚本触发的自包含目录 + 恢复时 REST 验证

知家 v1 的备份与恢复是发布加固阶段的核心交付。备选项之一是「应用内 OWNER 端点调 pg_dump」——它会给 UI 一颗备份按钮、与审计天然挂钩，但代价明显：app 镜像必须塞进 `postgresql-client`、JVM 必须 fork `pg_dump` 进程、需要挂载并 chown 备份输出卷，而恢复又注定不能在 app 进程内做（app 正占用着要恢复的库）。结论是不值得为一颗按钮承担这些代价。

决定：

- **备份** 由宿主机运维脚本（`scripts/backup.sh`，经 `make backup-test` 调用）触发，不经应用、不动 app 镜像。`pg_dump` 在 `postgres` 容器内执行；文件卷（含回收站中尚未物理删除的附件）由一次性容器镜像拷出。产物是一个自包含目录 `backup_<id>_<timestamp>/`，内含 `db.dump`、`files/`、`manifest.json`。`manifest.json` 记录 schema 基线（`flyway_schema_history` 最大版本）、应用版本（`ZIJA_VERSION`）、备份时间、`db.dump` 的 SHA256、每个文件的 `storage_key` + SHA256 + 字节数与孤儿计数。备份批次标识就是目录名。
- **恢复** 由宿主机运维脚本（`scripts/restore.sh`，经 `make restore-smoke` 调用）触达一套空环境（空 `postgres-data` 卷、空 `zija-files` 卷）的 Compose 栈：导入 `db.dump` → 拷入 `files/` → 启动 app（Flyway 跑迁移且 v1 下应为 no-op）→ 用备份中的所有者身份依次调三个 REST 端点断言：`/api/v1/system/info` 的版本 == manifest 应用版本、新增的 OWNER-only `GET /api/v1/files/integrity-report` 的 `missing`/`hashMismatch` == 0、既有库存 `checkConsistency` 端点的 `discrepancies` == 0。
- **文件完整性检查由 `file` 模块承担**：新增 OWNER-only `GET /api/v1/files/integrity-report` 遍历所有 file 行核对卷上文件存在且 SHA256 匹配，返回 `{checked, missing, hashMismatch, orphanCount}`。孤儿文件只告警不计入失败。`system` 模块不新增聚合端点、不引入对业务模块的依赖（守 spec §8.4）。

权衡与拒绝项：

- 拒绝「应用内 OWNER 端点」：镜像鳞膋、JVM fork、备份/恢复不对称、新增未在 spec §4.2 角色矩阵的能力项，四项代价不抵一颗按钮。
- 拒绝「运维脚本直连 DB 与文件卷绕过应用」做恢复验证：会绕过 `file` 模块的存储与哈希抽象，存储布局或算法一旦变化即漂移。恢复验证必须经过 app 的 REST 端点，让 app 自己声明它管理的数据是自洽的。
- v1 不交付升级冒烟（见 ADR-007）与性能验证（见 ADR-008），故本架构只处理「同版本备份 → 空环境恢复」一条链。