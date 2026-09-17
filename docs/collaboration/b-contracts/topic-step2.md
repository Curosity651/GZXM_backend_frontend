# 第 2 步：课题与成员实现及验收

## 实现范围

2026-09-15，在 `分支B` 实现 Topics 的 8 个接口。业务选择依据用户本轮明确确认；不代表 A/C 已完成跨模块评审。

| 接口 | 行为与权限 |
|---|---|
| GET /api/v1/topics | 分页、关键词、状态、启用筛选；管理角色可读全局，单位仅可读自身有效成员课题 |
| POST /api/v1/topics | 科研助理且具有 topic.manage；创建 ACTIVE、enabled=true 的课题和成员 |
| GET /api/v1/topics/{topicId} | 按数据范围读取详情和成员 |
| PUT /api/v1/topics/{topicId} | 科研助理维护，必须提交最新 recordVersion；原子更换牵头及增量添加成员 |
| PUT /api/v1/topics/{topicId}/status | 科研助理暂停、关闭、停用或恢复课题 |
| GET /api/v1/topics/{topicId}/members | 按课题数据范围读取成员，包括历史停用关系 |
| POST /api/v1/topics/{topicId}/members | 当前有效牵头单位且具有 topic-unit.manage，添加承担单位 |
| PUT /api/v1/topics/{topicId}/members/{membershipId}/status | 当前有效牵头单位启停承担关系，不能停用牵头关系 |

### 已落实的规则

- 创建时必须恰有一个启用的重点项目；零个或多个返回 409，提示配置项目。B 不自动创建项目。
- 牵头及新增承担单位必须存在且启用；承担列表不能重复或包含牵头。
- 编辑不传或传空 participantUnitIds 均保留已有关系；非空只增加新关系，已有停用关系不会因此恢复。
- 换牵头在同一事务内完成，旧牵头保留为承担单位，新牵头原有停用关系会恢复。唯一约束失败时整体回滚。
- 重复添加任何已有成员关系返回 409；停用承担单位通过成员状态接口恢复。
- 暂停、关闭或停用课题后，基本资料与成员操作返回 409；科研助理仍可使用状态接口恢复。
- 停用成员失去该课题读取权限；管理角色仍可查看历史。旧牵头的后续成员维护请求会被拒绝。
- 课题编辑使用版本冲突检测；成员与状态写入锁定课题行并递增版本。参数错误 422、未登录 401、越权 403、不存在 404、业务冲突 409。

### 开发边界

生产代码集中于 topic 的 api/application/domain/repository 及模块配置。单位信息通过 A 的公开 SystemService 获取，权限复用 CurrentUser/TopicAccessService，成功审计在业务事务提交后调用已有 AuditService。MapperScan 仅注册 topic.repository。

没有更改 POM、common、config、auth、既有迁移或 OpenAPI 公共模型。无需新增表。OpenAPI 只补充 Topics 操作的规则说明及错误响应。供 C 使用的公开查询契约仍属于第 3 步。

现有状态、成员动作请求没有客户端 recordVersion，因此行锁保证事务一致性，但不能检测操作人基于旧页面提交的新状态。公共请求模型的版本扩展仍需后续契约评审。审计继续使用已有独立事务服务；业务提交后审计失败的恢复机制属于公共基础能力范围。

## 测试记录

- Java 21：Maven 全量测试 48 项通过，0 失败、0 错误、0 跳过；其中新增课题集成测试 35 项。
- 使用隔离的本机 MySQL 8.4.3 实例与专用测试库，真实运行 Flyway、SQL、事务、Spring Security 和公共应用服务；未使用 H2 或模拟数据库。
- 覆盖全部 8 个接口、未登录、角色与动作权限正反例、跨课题隔离、停用与恢复、原子换牵头、重复关系、版本冲突、并发编辑仅一次成功，以及唯一约束失败后的整体回滚。
- OpenAPI 3.1 静态检查通过；从集成测试导出 8 个真实成功响应，逐个按原 OpenAPI 响应 schema 校验通过。
- 测试使用认证上下文构造角色，不等同于真实登录和前端端到端测试；本地验收需使用实际账号令牌。
- 前端 npm test：18 个测试文件、94 项通过；npm run lint 与 npm run build 均通过。本步骤未接入前端真实 API。测试环境存在 getComputedStyle 伪元素提示，构建存在超过 500 kB 的产物提示，均未导致检查失败。

后端复验：在 backend 下使用 Java 21 执行 `.\mvnw.cmd test`。默认通过 Testcontainers 启动 MySQL，需要 Docker；也可以指定隔离测试库：

```powershell
$env:GZXM_TOPIC_TEST_MYSQL_URL = 'jdbc:mysql://127.0.0.1:33379/gzxm_topic_test_step2?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
# 如需认证，在本机环境配置 GZXM_TOPIC_TEST_MYSQL_USER / GZXM_TOPIC_TEST_MYSQL_PASSWORD。
.\mvnw.cmd test -B -ntp
```

该 URL 仅为本次隔离实例示例，复验前必须自行启动测试 MySQL 并创建专用库。测试会清理该库的课题、成员、项目、单位及审计夹具；禁止指向部署库。代码只接受本机地址和 gzxm_topic_test_ 前缀库名。

仓库根目录运行契约检查（Python 环境需安装 openapi-spec-validator==0.7.2）：

```powershell
python docs/collaboration/b-contracts/validate.py
python docs/collaboration/b-contracts/validate-topic-responses.py
```

第二个脚本读取 Maven 测试生成的 backend/target/topic-contract-responses.json，需先完成后端测试。

## 在本地部署项目上验收

### 准备

1. 保持 `分支B`，使用 Java 21，按既有本地配置重新启动后端：仓库根目录执行 `scripts/run-backend.ps1`。先自行停止旧后端进程，避免端口占用。
2. 由现有项目初始化流程配置唯一启用项目，以及至少三个启用单位。准备科研助理账号和相应单位账号，分别具备上述动作权限。没有项目会按设计返回 409，不能只靠创建课题完成初始化。
3. 通过既有登录接口取得各账号 access token。使用 Postman 等 HTTP 客户端，对下列请求添加 `Authorization: Bearer <对应账号令牌>`，JSON 请求添加 `Content-Type: application/json`。基地址使用本机后端实际地址。
4. `/v3/api-docs` 可检查 Topics 的 8 个 operationId。现有前端仍有 Mock 数据，页面显示不作为此次后端验收依据。

### 创建、查询与编辑

科研助理发送 `POST /api/v1/topics`，把示例单位 ID 替换为本机真实启用单位 ID：

```json
{
  "code": "LOCAL-TOPIC-B-001",
  "name": "课题与成员本地验收",
  "summary": "仅用于本地功能验证",
  "leadUnitId": "1",
  "participantUnitIds": ["2"],
  "startDate": "2026-09-15",
  "endDate": "2027-09-15"
}
```

预期 201；记录返回的 id、recordVersion 及 members 中的关系 id。状态 ACTIVE、enabled=true、版本 1，且恰有一个 LEAD。重复编号创建返回 409。

使用 `GET /api/v1/topics/{id}` 和 `GET /api/v1/topics?page=1&size=20` 验证持久化；刷新或重新登录后仍可读取。单位 1、2 可读，其他无关系单位不可读详情且列表不含该课题。

科研助理 `PUT /api/v1/topics/{id}`：提交完整 code/name/leadUnitId 和刚查询得到的 recordVersion，可加 summary、日期、participantUnitIds。成功返回 200 且版本增加；相同旧版本再提交返回 409。该接口是完整资料更新，可选资料字段省略时可能被清空；只有 participantUnitIds 特别采用增量语义。

### 成员与状态

| 操作 | 请求与预期 |
|---|---|
| 添加第三单位 | 单位 1 POST /topics/{id}/members，body 为 {"unitId":"3"}，201；重复 409。路径均带 /api/v1 前缀 |
| 非牵头尝试添加 | 单位 2 执行相同操作，403 |
| 停用承担成员 | 单位 1 PUT /topics/{id}/members/{关系id}/status，{"enabled":false}，200；该承担单位再读课题被拒绝 |
| 恢复承担成员 | 同路径提交 {"enabled":true}，200；承担单位恢复读取 |
| 禁止停用牵头 | 使用 LEAD 关系 id 调用成员状态接口，409 |
| 换牵头 | 科研助理重新 GET 最新版本，再 PUT 完整资料并设置 leadUnitId 为单位 2；200，新 LEAD 为单位 2，单位 1 保留 PARTICIPANT |
| 旧牵头失权 | 单位 1 再维护成员，403；单位 2 可维护 |
| 暂停 | 科研助理 PUT /topics/{id}/status，{"enabled":true,"status":"PAUSED"}；随后编辑资料/成员返回 409，查询仍可用 |
| 恢复 | 科研助理同路径提交 {"enabled":true,"status":"ACTIVE"}，200，恢复写入 |
| 关闭或停用 | 分别测试 CLOSED 或 enabled=false，写入被拒绝；科研助理仍能恢复 |
| 非法输入/未登录 | 错误日期、非法 ID 返回 422；删除认证头后返回 401 |

所有测试课题应使用专用编号。验收完成可由科研助理停用保留历史，当前契约没有删除接口。
