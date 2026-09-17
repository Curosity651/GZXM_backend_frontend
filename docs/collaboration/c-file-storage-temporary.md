# C 文件存储临时实现说明（供协作人员和代码模型阅读）

状态：C 分支中的临时实现，尚未合并到主分支；本文描述当前代码，不代表最终 NAS 或对象存储方案。用户在本轮协作中明确要求由 C 实现文件服务、不调用 A 的文件功能。这覆盖了原协作分工中“A 负责 file”的安排，但合并前仍应由 A、B 检查公共接口兼容性。

## 代码边界与配置

- 服务入口：`backend/src/main/java/com/gzxm/server/modules/file/api/FileController.java`；业务校验：`application/FileService.java`；存储接口：`application/FileStorage.java`；当前适配器：`infrastructure/MockFileStorage.java`。类名含 `Mock`，但当前实现确实读写磁盘。
- 后端现有 `application.yml` 从环境读取 `FILE_PROVIDER`（默认 `MOCK`）、`FILE_MOCK_ROOT`（默认 `./storage/mock`）、`FILE_UPLOAD_TICKET_TTL`（默认 15 分钟）、`FILE_SIGNED_URL_TTL`（默认 10 分钟）。本次未修改配置文件、公共安全模块或迁移。
- `MOCK` 和 `FILESYSTEM` 都使用同一个磁盘实现。建议本地/目录挂载环境设 `FILE_PROVIDER=FILESYSTEM`，并把 `FILE_MOCK_ROOT` 设成**已存在、可写的绝对目录**；目录不合格时应用启动失败。默认 `MOCK` 的相对路径依赖后端进程工作目录，仅供演示或旧数据兼容。
- MySQL `file_object` 只保存 `storage_provider`、`bucket_name`、相对 `object_key`、文件名、类型、大小、可选 SHA-256、状态、上传者和时间；不保存文件字节或 NAS 绝对路径。新对象键由服务端生成，形如 `archive/<上传者ID>/<UUID>`，实际文件为 `<FILE_MOCK_ROOT>/<object_key>`。归档关系保存在 `archive_folder_file`。

## 上传、关联、读取流程

1. 已登录且有 `file.upload` 权限的用户 `POST /api/v1/files/upload-tickets`，传文件名、正整数大小、Content-Type、业务类型 `ARCHIVE`/`ACHIEVEMENT`，可选 SHA-256。服务端限制 100 MiB，建立 `PENDING` 元数据，返回短期 `PUT /api/v1/files/{id}/content?expires=...&signature=...` 地址。
2. 客户端带当前 Bearer 令牌、票据的 Content-Type 和原始字节 PUT。服务端再次确认上传者、有效期、HMAC 签名、`PENDING` 状态、长度、类型和可选 SHA-256，然后在目标目录写临时文件并重命名。签名由现有 `JWT_SECRET` 派生，未新增密钥配置。这个地址**不是匿名可用的预签名 URL**，离开当前登录会话不能上传。
3. 上传者 `POST /api/v1/files/{id}:complete`。服务端从存储重新读取字节，核对长度和可选 SHA-256，才把状态置为 `READY`。然后归档模块调用文件服务的 `requireArchiveOwnedReady`，检查当前上传者、READY 和服务端生成的 `archive/` 前缀，再将文件 ID 关联到本单位有权操作的文件夹。
4. 下载/预览先经 `GET /api/v1/files/{id}/download-url` 或 `/preview-url` 取得短期地址，再带 Bearer 令牌 GET `/content`。文件服务验证签名、READY 和读取权限。上传者和全局角色可读；其他人只有在 `ArchiveFileReadPolicy` 找到有效归档关联且通过课题/单位范围检查时可读。HTML/SVG 等类型即使请求预览也强制附件下载；仅 PDF、PNG、JPEG、GIF、WebP 可内联，响应设置 `X-Content-Type-Options: nosniff`。
5. 从归档文件夹“移除文件”只软删除 `archive_folder_file` 关系，不立刻删除 `file_object` 或磁盘对象，因为同一文件可能有其他引用。目前没有孤儿文件清理任务。

## 数据库操作与 ACID 边界

本实现不是只有页面逻辑。C 的业务服务通过 `JdbcTemplate` 参数化 SQL 读写现有 MySQL 表：报告服务读写 `topic_report_rule`、`report_task`、`progress_report`、`approval_record`、`submission_snapshot` 和 `api_idempotency`；归档服务读写 `archive_folder`、`archive_folder_file`、`self_funded_project`，并查询文件关联和创建者角色；文件服务通过 MyBatis `FileObjectMapper` 读写 `file_object`。数据库表和外键来自已有基线迁移，本分支没有修改迁移。

- **原子性（Atomicity）**：报告创建、草稿更新、提交快照/幂等记录、审批状态/审批记录、归档目录创建/删除/文件关联、自筹项目创建及模板目录创建，都在 Spring `@Transactional` 方法内。中途抛出业务异常时，数据库写入回滚。目录入口在懒加载预置目录时也包在事务内。
- **一致性（Consistency）**：外键、唯一键和检查约束由 MySQL 执行；例如报告期次、规则年度、快照版本、文件夹与文件关联、自筹项目编号、幂等键都有约束。服务层补充课题/单位数据范围、状态、角色、文件 READY、大小和 SHA-256 校验。自筹项目更新使用 `record_version` 条件 UPDATE，受影响行不是 1 就返回版本冲突。
- **隔离性（Isolation）**：报告规则、报告编辑、提交和审批会对业务行使用 `FOR UPDATE` 或乐观版本检查；归档删除/关联会锁定文件夹行，避免检查“空文件夹”后并发写入。实际隔离级别仍由 MySQL 数据源配置决定（通常为 InnoDB 默认 `REPEATABLE READ`），本模块没有擅自改全局隔离级别。
- **持久性（Durability）**：事务提交后，MySQL 的状态、快照、审批、关联和元数据由 InnoDB 持久化；提交前异常不会留下半个数据库业务动作。数据库提交不等于磁盘/NAS 文件和数据库的跨系统原子提交。

文件上传是数据库和文件系统之间的两阶段业务流程，不能宣称跨系统 ACID：第一步只提交 `file_object=PENDING`；第二步写入临时文件并重命名；第三步重新读取并校验后把元数据改为 `READY`。如果磁盘写入成功但第三步失败，会留下 PENDING 记录/对象；如果数据库提交成功后 NAS 随后损坏，数据库仍可能是 READY。后续需要定时清理 PENDING/孤儿对象、存储健康检查和恢复对账。归档关联只在数据库事务内提交，关联失败不会删除已上传对象，因为对象可能被其他业务引用。

前端归档上传/下载调用位于 `frontend/src/api/file-api.ts`。当前真实 API 模式页面已调用这些接口，但全局登录页/路由仍采用 Mock 会话，真实 API 浏览器端到端流程尚未接入；真实后端接口已独立联调。文件字节不要放入归档表，也不要让业务模块自行拼接磁盘路径。

## 切换到 NAS 的条件

**NAS 作为已挂载目录（NFS/SMB）**：暂停写入，创建并核实挂载点；把现有 `<root>/<object_key>` 完整复制到新目录并核对数量/校验值，保留原目录备份；将 `FILE_MOCK_ROOT` 指向新挂载目录并用 `FILE_PROVIDER=FILESYSTEM` 重启；抽样验证旧、新文件上传与下载，再恢复写入。当前适配器同时识别数据库内旧 `MOCK` 和新 `FILESYSTEM` 记录，前提是**同一相对对象键在新根目录下确实存在**。只改环境变量、不复制旧对象，会造成旧文件无法读取。

**NAS 仅提供对象/API/S3 接口**：目前不能直接切换。需新增 `FileStorage` 适配器及按 `file_object.storage_provider` 选择读取实现的路由，同时处理旧对象迁移、签名地址、鉴权和回滚。当前服务只有一个注入的存储实例，`MOCK`/`FILESYSTEM` 共用一个磁盘根目录；它没有对象存储客户端。

当前路径存在性与可写性只在 `FILESYSTEM` 启动时检查，不会在每次操作时验证它仍是原 NAS 挂载。如果挂载掉线而本地挂载点可写，可能写到本地目录；上线 NAS 前须增加挂载健康检查和运维监控。实现使用整文件 `byte[]`，100 MiB 文件会占用相应堆内存，较大文件或高并发应改流式传输。上传票据超时或用户未调用 `complete` 可能留下 `PENDING` 元数据和临时孤儿对象，需后续清理策略。

## 与其他模块的接口和评审点

- 本分支改了 `FileStorage.createUploadUrl/createDownloadUrl` 的方法签名并新增 `write/read`；当前仓库只有 `MockFileStorage` 实现，其他业务代码未直接使用 `FileStorage`，但 A 的并行分支若新增实现需要对齐。`FileService` 保留了原四参数构造器供测试，生产构造器接收读取策略列表。
- C 的归档通过 `FileService` 公开方法接入，没有读文件 Mapper。B 的成果模块仍通过其自己的 `AchievementFileGateway` 抽象；当前仓库没有真实适配器，非空成果附件请求仍不能宣称完成。B 的待评审契约见 `docs/collaboration/b-contracts/achievement-file-foundation.md`。
- `docs/api/openapi.yaml` 已描述新字节端点和授权，但较早的 `docs/api/api-reference.md` 仍是原协作口径；合并评审需统一文档。前端二进制请求目前直接从 `sessionStorage` 取令牌，未来若公共 HTTP 客户端切换令牌提供方式或要求自动刷新，需由 A 提供统一的二进制请求能力。
- 后端 `FileServiceTest`、`MockFileStorageTest` 和 `FileControllerTest` 覆盖归属、READY、长度/校验、迁移后读取及危险类型预览；实际 API 联调覆盖上传、归档关联和下载。正式 NAS 切换仍需在目标环境做挂载断开、迁移校验和并发测试。
