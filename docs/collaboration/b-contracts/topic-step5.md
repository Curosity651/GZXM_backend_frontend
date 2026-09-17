# 第 5 步：单位指标分配实现与验收

2026-09-16，在分支B完成三个单位分配接口。延续第 4 步草稿、生效值和不可变发布历史分离的方式；不改 main，不代签 A/C 合并前审查。

## 接口和权限

以下路径统一以 /api/v1 为前缀。

| 接口 | 行为 |
|---|---|
| GET /topics/{id}/unit-allocations?nodeId=... | 默认 view=effective，管理角色和当前牵头读取全课题，承担单位只读取本单位；查询在 SQL 中附加单位范围 |
| GET 同路径加 view=draft | 仅当前有效、启用的牵头单位且具有 unit-allocation.manage；返回 X-Draft-Version 和 X-Topic-Indicator-Version |
| PUT /topics/{id}/unit-allocations | 当前有效牵头且具有 unit-allocation.manage，完整替换该节点分配草稿 |
| POST /topics/{id}/unit-allocations:publish | 当前有效牵头且具有 unit-allocation.publish，发布已核对的草稿；成功或成功重放 204 |

牵头是课题关系，不是内部单位专属权限：外部单位担任当前牵头时同样可以维护。管理角色即使被误配分配动作权限也不能代牵头写入。成员停用后不能继续读取，换牵头后旧牵头只可读自己的生效分配，不能写入或重放原发布请求。

## 保存和下发规则

- 首次保存 draftVersion 省略或 0，后续必须匹配最新 X-Draft-Version，否则 409。同版本并发编辑仅一次成功。
- allocations 完整替换草稿；省略项移除、空数组清空，均不改变旧生效分配。草稿可以分步填写，暂时缺成员或合计不足；发布才要求完整覆盖和足额。
- 单位必须是有效成员且系统单位启用；服务器通过公开服务查询成员，通过已有生效课题指标解析 membership_id 和 topic_indicator_id，不接受客户端指定这些关联 ID。
- 课题该节点必须先有 PUBLISHED 指标。课题草稿不能作为分配依据。节点必须属于课题项目并启用，分配的定义必须已下发且启用。
- 每行单位/定义组合唯一；数量为 0..2147483647 整数，拒绝负数、小数和数字字符串；单批最多 5000 行。
- 每个单位每个指标按节点累计不递减，同时检查前序和后续；发布再次检查当前生效值。专项不能超过同单位同成果类型的基础分配，多个专项可能重叠，不重复相加。
- 发布必须覆盖所有有效且启用成员（包含牵头）和该节点全部生效指标。单位没有分配任务时显式填 0，不能省略该行。
- 每项指标所有有效成员的数量之和必须大于等于课题要求，允许超额；求和使用 long，避免多个大整数溢出。
- 保存记录当前课题指标发布版本。若课题指标后来重新下发，原分配草稿发布返回 TOPIC_INDICATOR_VERSION_CHANGED（409），须核对新要求并重新保存。
- 成员新增、停用、单位停用均在发布时重新验证。旧草稿不能绕过最新成员范围。停用成员的旧生效分配保留为历史，不计入新发布的覆盖和合计。
- 暂停、关闭、停用课题不能新增保存或发布；历史仍按数据范围可读。仍有权限的牵头可重放原成功发布，但旧牵头或被停用单位不能重放。

### 当前调整限制

沿用第 4 步保护已生效要求的限制：本阶段不允许下发降低有效成员已有分配或删除其既有指标的草稿，返回 409。草稿可降低后继续编辑，但不会改动生效值。将来开放降额/重分配时，需接入成果实际完成量下限及调整规则；当前的“不得低于已生效分配”更严格，不应宣称已实现按实际完成量自由调减。

停用成员的历史行不会被删除，管理角色/牵头查询可能同时看到旧版本历史行与当前有效成员的新版本行。后续统计不能直接对全部历史行求和，应结合有效成员、单位启用状态和业务口径；本步没有实现第 8 步统计服务。

## 事务、重试与边界

- 保存、发布均使用 TopicQueryService.lockTopic 的公开事务锁，与课题状态、成员修改和课题指标发布串行协调；不访问 topic Mapper。
- 发布将所有生效分配与发布历史在同一事务提交；更新保留原分配行 ID。第二行失败时，第一行、历史及版本全部回滚。
- Idempotency-Key 为 8..100 个可见 ASCII 字符。同一用户的键在单位分配发布操作内唯一；同课题/节点/草稿版本重放 204，不增加版本、不重复成功审计；同键不同请求 409。旧请求重试不发布新草稿。
- 去重保存在 B 的不可变业务发布历史中，不访问 A 的 api_idempotency，不建立通用幂等框架。审计沿用事务提交后的公共 AuditService；公共审计故障恢复仍由 A 统一处理。
- 草稿版本与发布版本不同；编辑使用 X-Draft-Version，请勿使用响应行 version。X-Topic-Indicator-Version 表示保存时绑定的课题指标版本。
- 未改公共 POM/auth/common/config、前端公共代码或既有迁移；单位通过 A 的 SystemService 查询。新增迁移及契约仍需真实的 A/C 合并前评审。

## 数据库

新增 `V202609160200__add_allocation_drafts_and_publications.sql`：

| 表 | 用途 |
|---|---|
| unit_allocation_draft | 课题/节点草稿头，编辑版本、发布版本及绑定的课题指标版本 |
| unit_allocation_draft_item | 单位/指标草稿明细，唯一组合及非负约束 |
| unit_allocation_publication | 不可变分配快照、请求键、操作者、发布版本及课题指标版本 |

原 unit_indicator_allocation 继续保存生效分配。迁移导入旧 DRAFT 明细，保留旧发布版本并生成已有生效值的基准快照；基准导入操作者 0、请求键 baseline-*。无法恢复旧系统未保存的更早历史。

## 验证结果

- Java 21 全量 Maven **81 项通过**，0 失败、0 错误、0 跳过；新增 UnitAllocationIntegrationTest **16 项**。
- 实际 MySQL 8.4.3：涵盖牵头/承担/管理权限、内部与外部牵头、草稿替换/清空、合计不足/等额/超额、零行覆盖、大数求和、专项、跨节点累计、非法单位/节点/维度、课题目标更新、成员变更、状态只读、并发编辑、并发重放和故障回滚。
- 空库由 Flyway 顺序执行三条迁移成功；后续全量测试验证重复启动不重复迁移。另在隔离升级库预置旧分配发布版本 9、课题目标版本 7 和旧草稿，执行增量后数量、版本、草稿、目标绑定、快照均保留。
- OpenAPI 3.1 静态检查以及本步 3 个真实响应 schema 校验通过；既有 8 个课题、5 个课题指标响应继续通过。
- 前端 **94 项通过**，lint、build 通过。原有伪元素环境提示和构建大包提示仍存在，不影响通过。

测试使用模拟认证上下文与真实应用/数据库，未替代真实登录和前端端到端验收。日志中 synthetic allocation failure 是事务回滚测试的预期故障注入。

后端复验：Java 21，在 backend 执行 `.\mvnw.cmd test -B -ntp`。默认使用 Docker/Testcontainers；无 Docker 可配置 GZXM_TOPIC_TEST_MYSQL_URL 指向本机 gzxm_topic_test_* 隔离库，用户名/密码用对应环境变量。测试会清理夹具，不能使用部署库。

Maven 通过后，用已安装 openapi-spec-validator==0.7.2 的 Python 在仓库根目录执行：

```powershell
python docs/collaboration/b-contracts/validate.py
python docs/collaboration/b-contracts/validate-topic-responses.py
python docs/collaboration/b-contracts/validate-indicator-responses.py
python docs/collaboration/b-contracts/validate-allocation-responses.py
```

## 本地部署验收

1. 在分支B使用既有 `.env`、Java 21 重启后端，例如运行 `scripts/run-backend.ps1`。Flyway 自动应用新迁移，不需要手动重复建表。本次开发未迁移部署数据库。
2. 准备可操作课题、至少一个承担单位和牵头账号，先通过第 4 步发布课题指标。查询成员和生效课题指标，取得真实 unitId/nodeId/indicatorDefinitionId。
3. 使用牵头令牌调用 PUT /api/v1/topics/{课题ID}/unit-allocations，添加 Authorization 与 Content-Type。下例假设课题该节点指标要求为 5、有效成员只有单位 1 和 2，必须用本机实际数据替换：

```json
{"nodeId":"1","draftVersion":0,"allocations":[{"unitId":"1","indicatorDefinitionId":"1","targetQuantity":2},{"unitId":"2","indicatorDefinitionId":"1","targetQuantity":3}]}
```

预期 200，读取 X-Draft-Version=1 和 X-Topic-Indicator-Version。草稿 GET 同路径加 `?nodeId=1&view=draft` 可见两行；承担单位此时只能 GET 默认生效列表，尚无发布时为空。

4. POST /api/v1/topics/{课题ID}/unit-allocations:publish，增加 `Idempotency-Key: local-allocation-0001`：

```json
{"nodeId":"1","draftVersion":1}
```

预期 204。牵头 GET `?nodeId=1` 可见两行，承担单位只能看自己的数量 3。重复原请求仍 204，行 version 不增加。

5. 使用最新草稿版本修改后保存，成员仍看到旧生效值；使用新请求键发布后才切换。将合计改为 4，草稿可保存但发布 422。省略牵头行，即使其他单位合计足额也返回 422；没有任务应填 0。
6. 科研助理上调并重新发布课题要求，旧分配草稿发布应返回 409，重新核对并保存才能发布。换牵头后旧牵头写入 403；停用成员读取 403；暂停课题后写入 409。

当前没有新页面或发布历史 HTTP 入口，前端仍未接入真实 API，须用 Postman 等 HTTP 客户端验证。历史可在本机测试库只读查询 unit_allocation_publication；单位分配工作流的真实页面联调属于第 9 步。
