# 第 6 步：成果草稿与材料契约

日期：2026-09-16；开发分支：`分支B`。

后续更新：状态动作、审批、快照和待办现已在[第 7 步](achievement-step7.md)实现；下文保留第 6 步交付时的范围与测试记录，文件依赖限制仍有效。

**当前结论：成果草稿后端已实现并通过测试；第 6 步整体尚未完成。** 用户明确要求保持 B 边界，文件能力等待 A 提供。因此本次没有修改 A 的 file/common/config/auth/system、公共 POM、前端公共代码或既有迁移，也没有实现真实材料上传关联和跨单位下载闭环。

## 1. 已实现内容

| 接口（前缀 /api/v1） | 行为 |
|---|---|
| GET /achievements | 分页、课题/节点/单位/指标/状态过滤；SQL 同时约束结果与 total 的数据范围 |
| POST /achievements | 创建五类成果草稿；201，初始 DRAFT、recordVersion=1、submittedVersion=0、countsToIndicator=false |
| GET /achievements/{id} | 查询五类详情和材料关联历史；当前无附件草稿可正常使用 |
| PUT /achievements/{id} | 仅所属单位编辑 DRAFT；完整替换 title/responsiblePerson/detail，校验最新 recordVersion，成功递增 |

- 创建必须由具有 `achievement.submit` 权限的内部/外部单位账号操作；所属单位启用、课题成员关系有效、课题可操作。
- 必须绑定当前单位在该节点已经下发的 BASE 指标；课题指标也须已下发，零目标允许。节点须属于当前课题项目并启用。
- projectId、membershipId、unitId、achievementType 由服务端推导。客户端多传 unitId/status/countsToIndicator 不会改变归属、状态和计数。
- 创建后课题、单位、节点、指标和成果类型不能改。更新仍核对有效成员、单位启用状态及下发指标。
- 单位只读自己的成果；当前牵头可读本课题各单位成果；管理角色可全局读取。牵头不能替其他单位编辑，管理角色也不能冒充单位填报。
- 课题暂停、关闭或停用后历史只读。停用成员失去读取范围；旧牵头失去其他单位数据范围。
- B 在 TopicQueryService 增加 `canReadTopic`，校验身份中成员信息和数据库当前关系，供列表安全过滤及将来的文件授权使用。避免捕获被代理事务标记回滚的权限异常造成 500；不从 achievement 访问 topic Mapper。
- 创建、编辑通过课题公共锁协调成员与指标变更；更新再锁成果并比较版本。并发编辑同一版本只能有一个成功，另一个 409。
- 成功审计沿用公共 AuditService 在业务提交后记录；公共审计故障恢复仍由 A 负责。

## 2. 五类详情

用户确认以当前前端五类表单字段为清单，统一放入 `detail`。完整字段列表与约束已落到 OpenAPI 的五种 AchievementDetail schema 及服务端白名单。

| 类型 | 示例字段 | 本步校验 |
|---|---|---|
| PAPER | paperStatus、paperType、paperFormType、issn、submissionDate、isChineseCoreJournal | 字符串、已知枚举、日期、布尔值 |
| PATENT | patentStatus、patentScope、applicationDate、inventorList | 同上；申请、受理、授权日期顺序 |
| COPYRIGHT | version、registrationNumber、completionDate、isPowerGridFirstCompleter | 类型、长度和日期顺序 |
| STANDARD | standardLevel、currentStage、draftSubmissionDate、draftCommitDate | 类型、长度和日期顺序；表单自由文本不擅自改为枚举 |
| TALENT | studentName、educationLevel、enrollmentDate、expectedGraduationDate | 学历枚举、类型、长度及日期顺序 |

草稿可以省略 detail 或填写 `{}`；业务详情暂不要求完整。已填值仍校验：跨类型/未知字段 422，布尔值不能用字符串代替，日期须有效 YYYY-MM-DD；普通文本最多 500 字符，指定长文本最多 10000 字符，整个 JSON 最多 65535 UTF-8 字节。null 和空白值被规范化为未填。标题、负责人仍必填，分别最多 500、100 字符。

**PUT 的 detail 为完整替换，省略会变为 `{}`。** 正式提交必填项、状态动作、审批、退回后编辑、提交快照及进度统计留待后续步骤；`pendingForMe=true` 当前返回空分页，不能据此验收审批待办。

## 3. 材料：已预留与尚未接通

请求使用结构化 `materialAttachments`：

```json
[{"fileId":"123","materialType":"论文附件"},{"fileId":"124","materialType":"论文附件"}]
```

契约：省略保留当前材料，空数组清空当前关联但保留旧关联；非空数组完整替换。允许同类多文件，单次最多 100 个，不能重复 fileId。改变集合时递增材料版本、退役旧集合；未改变集合则不增加材料版本。旧 `materialFileIds` 非空请求返回 422，防止丢失材料类别。

新增 `V202609160300__version_achievement_material_sets.sql`：

- achievement_material 新增 active，扩展唯一键使同类同版本能关联多个文件。
- 迁移保留旧关联，将每个成果、每种材料类别的最新旧版本设为当前。
- `materials` 计划返回当前文件元数据，`materialLinks` 返回材料关联及历史；清空不物理删除文件。

**运行限制：** B 的 AchievementFileGateway 目前没有生产适配器。非空附件请求返回 503 / `FILE_REFERENCE_CAPABILITY_UNAVAILABLE`，整个创建或编辑事务回滚。若旧库已有当前材料，包含这些记录的详情/列表也会返回 503，因为不能越权读取 A 的私有元数据。不要通过清空历史附件绕过此依赖。

B 已预留材料关联版本逻辑和 `canReadFile`，但没有声称真实关联和下载授权通过验收。A 需先按[文件公共能力提案](achievement-file-foundation.md)提供归属/READY/业务类别校验、可读元数据和授权回调；随后 B 添加适配器，补齐真实材料替换、历史可读、跨单位及旧牵头下载等测试。材料版本不是第 7 步的不可变提交快照。

## 4. 测试结果

- Java 21 全量 Maven：**98 项通过，0 失败、0 错误、0 跳过**；其中新增 AchievementIntegrationTest **17 项**。
- 真实 MySQL 8.4.3 + Spring Boot/MockMvc：五类草稿、零分配、五类详情、非法值、伪造归属、角色/数据权限允许与拒绝、旧牵头和停用成员旧身份、版本冲突、较大版本号、并发编辑、只读状态、文件依赖缺失时创建/更新回滚及无成功审计。
- 空库 Flyway 顺序执行四条迁移；重复启动验证不重复迁移。另在独立合成升级库预置三条旧材料关联，验证旧关联全部保留、最新版本标记正确，以及同类同版本多文件可插入。
- OpenAPI 静态校验及本步四个真实无附件成功响应通过；同时回归此前课题、课题指标、单位分配响应。
- 前端 **94 项测试通过**，`npm run lint`、`npm run build` 通过。原有伪元素环境提示和大包提示仍存在。

测试使用模拟认证和真实数据库，不替代真实登录和前端端到端验收。材料成功链路尚未验证；A/C 人工评审尚未完成。日志中的 synthetic failure / synthetic allocation failure 属于既有回滚测试故障注入。

复验：Java 21，在 backend 执行 `./mvnw test`（Windows 为 `.\mvnw.cmd test`）；默认需要 Docker/Testcontainers。无 Docker 可用 GZXM_TOPIC_TEST_MYSQL_URL 指向本机 `gzxm_topic_test_*` 隔离库；测试会清空夹具，不能指向部署库。然后使用已安装 openapi-spec-validator==0.7.2 的 Python 执行：

```powershell
python docs/collaboration/b-contracts/validate.py
python docs/collaboration/b-contracts/validate-topic-responses.py
python docs/collaboration/b-contracts/validate-indicator-responses.py
python docs/collaboration/b-contracts/validate-allocation-responses.py
python docs/collaboration/b-contracts/validate-achievement-responses.py
```

## 5. 本地部署与功能验证

1. 在分支B使用既有本地配置和 Java 21 重启后端，例如 `scripts/run-backend.ps1`。Flyway 自动应用新材料迁移；不要手动重复执行基线脚本。本次开发仅操作隔离测试库，没有迁移部署库。应用与数据库 DATETIME 使用的时区需一致，成果接口按应用时区输出 RFC3339。
2. 准备有效可操作课题和单位账号，先完成第 4 步课题指标下发、第 5 步单位分配下发。查询真实 topicId、nodeId、BASE indicatorDefinitionId，下面的 1 必须替换成自己的值。
3. Postman 等客户端用单位登录令牌，添加 `Authorization: Bearer <本地令牌>`、`Content-Type: application/json`，请求 POST `/api/v1/achievements`：

```json
{
  "topicId":"1",
  "nodeId":"1",
  "indicatorDefinitionId":"1",
  "title":"本地验证论文",
  "responsiblePerson":"测试负责人",
  "detail":{"paperType":"SCI","paperStatus":"撰写中","issn":"1234-5678"},
  "materialAttachments":[]
}
```

预期 201；确认 unitId 为登录单位、achievementType=PAPER、status=DRAFT、recordVersion=1、不计完成量。也可用其他四类实际指标 ID 和对应 detail 测试。不要给其他类型指标传论文字段。

4. GET `/api/v1/achievements/{返回的id}` 与 GET `/api/v1/achievements?topicId=实际课题ID`，检查内容与 total。普通其他单位访问详情 403，列表不包含该记录；当前牵头能读取，但替本单位编辑 403。
5. PUT `/api/v1/achievements/{id}`，提交完整创建字段并增加 `"recordVersion":1`、修改标题。预期 200、版本 2。重放旧版本返回 409；携带最新版本改节点/指标也返回 409。
6. 将论文 paperType 改为不存在的枚举，或添加人才 studentName 字段，预期 422。暂停课题后仍可查看历史，但编辑 409。无已下发基础指标时创建 409。
7. 加上非空 materialAttachments 后请求：当前应返回 503。再次 GET 验证原标题和版本未变化；失败创建不会产生新草稿。此步骤验证拒绝与回滚，不能视为材料成功联调。

**界面验证限制：本次未接入前端真实成果 API，现有页面仍为原型/Mock，点击页面不能证明这些后端接口已生效。** 当前使用 HTTP 客户端验收草稿功能；前端真实联调属于第 9 步，材料成功闭环等待 A。
