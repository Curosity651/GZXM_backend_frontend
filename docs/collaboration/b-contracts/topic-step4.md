# 第 4 步：课题指标实现与验收

2026-09-16：用户确认节点完整替换草稿、空列表清空；旧发布版本继续生效，修改另存草稿，重新下发原子切换并保留历史。五个设计接口已实现并测试。A/C 合并前契约与迁移审查未代签。

## 接口

以下路径前缀均为 /api/v1。

| 接口 | 行为 |
|---|---|
| GET /time-nodes | page:topic-indicator 权限；唯一有效项目的启用节点，按 sortOrder/id 排序；项目配置异常 409 |
| GET /indicator-definitions | 同页面权限；启用的基础/专项定义，按 id 排序 |
| GET /topics/{id}/indicator-targets?nodeId=... | 默认 view=effective，返回生效目标；有效成员及管理角色可读历史 |
| GET 同路径加 view=draft | 仅科研助理且具有 indicator.manage；X-Draft-Version 响应头给出节点草稿版本，无草稿为 0 |
| PUT /topics/{id}/indicator-targets | 科研助理且具有 indicator.manage；完整替换草稿，响应头给出新版本 |
| POST /topics/{id}/indicator-targets:publish | 科研助理且具有 topic-indicator.publish；携带 nodeId、draftVersion 和 Idempotency-Key；成功或成功重放 204 |

## 业务规则

- 首次保存 draftVersion 省略或为 0；后续必须匹配最新版本，否则 409。同版本并发保存仅一次成功。
- 省略行从草稿移除，空 targets 清空草稿；不影响旧生效值。空草稿不能发布。
- 同一指标后续节点累计值不得低于前序；修改早期节点同时检查后续。保存检查其他生效目标及待发布草稿，发布再次检查生效目标。
- 写入节点必须属于课题项目并启用，定义必须启用。数量为 0..2147483647 整数，不接受小数、数字字符串；最多 500 行，不能重复维度。
- 专项必须同时有唯一同类型基础目标，数量不超过基础；多个专项可能重叠，不将其相加作为基础总量。
- 暂停、关闭、停用课题后不能保存或新发布，历史仍按范围可读；停用成员失去读取资格。非科研助理即使误配动作权限也不能写入。
- **当前不能下发删除或降低既有生效目标的草稿，返回 409。** 草稿仍可清空或降低以便编辑，旧生效目标不变。开放降额前需落实成果完成量下限和单位分配调整契约，不能只检查相邻节点。

草稿版本 draftVersion 与发布版本 version 不同：保存增加前者，成功发布增加后者。草稿响应行的 version 表示最近发布版本，编辑冲突使用 X-Draft-Version。

发布历史保存用户、请求键、课题、节点、草稿版本和不可变目标快照。相同用户同键同请求重试 204，不重复更新或增加版本，不重复成功审计；同键不同请求 409。编辑后重试旧成功请求只重放原结果，不发布新草稿。相同用户的发布键不能跨课题/节点复用。重试前仍检查当前科研助理角色和发布权限，已成功请求即使课题后来只读也可重放。

历史与生效值在同一 MySQL 事务提交，失败全部回滚。这是 B 发布历史内的持久化去重，不访问 A 的 api_idempotency，不提供通用幂等服务；A 的公共基础能力后续接入仍需统一协调。审计在业务事务返回后调用公共 AuditService；审计故障补偿仍属于公共基础任务，本步未实现 outbox。

## 数据与开发边界

新增 `V202609160100__add_indicator_drafts_and_publications.sql`，没有修改既有迁移：

| 表 | 用途 |
|---|---|
| topic_indicator_draft | 每课题/节点一条草稿头，草稿版本、已发布草稿版本和发布版本 |
| topic_indicator_draft_target | 草稿明细，同草稿/定义唯一，非负数量 |
| topic_indicator_publication | 发布快照、版本、操作者和请求键，仅插入历史 |

原 topic_indicator 继续保存生效值，更新保留原行 ID，避免单位分配外键失效。迁移导入旧 DRAFT，保留旧发布版本，给已有 PUBLISHED 目标生成基准快照；不能恢复旧系统从未记录的更早历史。导入快照用 actor=0、baseline-* 标识。

TopicQueryService.lockTopic 是要求已有写事务的公开锁接口，协调课题状态变更与指标写入。indicator 不访问 topic Mapper；精准扫描 indicator.repository。未改 POM、公共配置、auth/common 或 A/C 模块。

实际节点日期、专项定义和匹配规则仍由既有项目初始化流程配置，不编造业务种子数据。新迁移合并前需 A 协调顺序和时间戳；本实现不等于 A/C 已批准。

## 测试记录

- Java 21 Maven 全量 **65 项通过**，0 失败/错误/跳过；本轮新增 11 项，topic/indicator 集成测试合计 52 项。
- 真实隔离 MySQL 8.4.3：草稿替换/清空、累计前后约束、权限正反例、专项边界、非法参数、节点归属、停用范围、并发保存/发布、旧请求重放及同键冲突。
- 注入发布第二行失败，验证首行生效值、历史和发布版本一起回滚，随后原键可以成功重试。测试日志 synthetic failure 是预期故障注入。
- 空库 Flyway 两条迁移通过，重复启动不重复迁移。独立升级测试预置发布版本 7 和旧草稿，执行新 SQL 后版本、数量、草稿、基准快照均保留。
- OpenAPI 3.1 静态检查通过；8 个课题和本步 5 个指标接口真实响应 schema 校验通过。
- 前端 94 项、lint/build 均通过；既有伪元素测试提示和构建大包提示仍存在。

后端复验沿用第 2 步隔离库说明，在 backend 使用 Java 21 执行 `.\mvnw.cmd test -B -ntp`。默认 Docker/Testcontainers，或通过 GZXM_TOPIC_TEST_MYSQL_URL 指定本机 gzxm_topic_test_* 专用库。测试清理夹具，不能指向部署库。

完成 Maven 测试后，在仓库根目录用安装了 openapi-spec-validator==0.7.2 的 Python 执行：

```powershell
python docs/collaboration/b-contracts/validate.py
python docs/collaboration/b-contracts/validate-topic-responses.py
python docs/collaboration/b-contracts/validate-indicator-responses.py
```

## 本地部署验收

1. 在分支B使用既有 `.env` 与 Java 21 重启后端，例如 `scripts/run-backend.ps1`。Flyway 自动应用新迁移，不要手动重复建表。此次开发没有迁移部署数据库。
2. 准备 ACTIVE 且启用的课题、启用节点、基础定义、科研助理及有效成员账号。通过目录接口取得真实 ID；空目录需先由项目初始化流程配置。
3. HTTP 客户端设置 `Authorization: Bearer <科研助理令牌>` 和 `Content-Type: application/json`。以下 ID 均须替换为本机值。

首次 `PUT /api/v1/topics/{课题ID}/indicator-targets`：

```json
{"nodeId":"1","draftVersion":0,"targets":[{"indicatorDefinitionId":"1","targetQuantity":2}]}
```

预期 200、X-Draft-Version=1。成员 GET 同路径 `?nodeId=1` 仍是旧生效值或空列表；科研助理 GET `?nodeId=1&view=draft` 可见草稿 2。

`POST /api/v1/topics/{课题ID}/indicator-targets:publish`，加头 `Idempotency-Key: local-publish-0001`：

```json
{"nodeId":"1","draftVersion":1}
```

预期 204。成员再 GET 生效列表可见数量 2、PUBLISHED、version=1。重复原请求仍 204，版本不变。

再次保存时携带 draftVersion=1，把数量改为 3，新草稿版本为 2，成员暂时仍看见 2。以新键 local-publish-0002 发布 draftVersion=2 后，成员看见 3、version=2。

还应验证：旧保存版本 409；旧发布键携带新版本 409；后续节点小于前序 422；非科研助理写入 403；无关系单位读取 403；暂停后写入 409；空草稿发布 422。草稿版本每次重新读取，不能一直照抄示例。

可在本机测试库只读查看历史：

```sql
SELECT topic_id,node_id,draft_version,publish_version,targets_json,published_at
FROM topic_indicator_publication ORDER BY id;
```

没有新增历史查询 HTTP 接口，前端页面仍未接入真实指标 API，不能用 Mock 页面验收。第五步单位分配不在本步骤实现。
