# 第 8 步：成果统计与对外查询服务

日期：2026-09-17；分支：`分支B`。

本步实现成果进度接口，以及供 C 注入的指标目标、生效成果和进度服务。统计不调用文件元数据或下载服务；第 6/7 步真实材料关联及正式提交仍等待 A，不能把统计通过理解为文件闭环已完成。

## 1. 交付入口

| 入口 | 用途 |
|---|---|
| GET /api/v1/achievement-progress | nodeId 必填，topicId、unitId 可选；按当前用户范围返回累计进度 |
| IndicatorProgressQuery.node(nodeId) | 当前配置项目节点；不存在 404，跨项目 422 |
| IndicatorProgressQuery.targets(topicId, nodeId, unitId) | 当前节点生效目标及版本、可见单位、历史标志、累计节点和定义 |
| AchievementProgressQuery.progress(topicId, nodeId, unitId) | 与 HTTP 相同的统计结果 |
| AchievementProgressQuery.effectiveAchievements(topicId, nodeId, unitId) | 累计已生效成果的最小引用，不含材料或文件地址 |
| TopicQueryService.listReadableTopics(projectId) | 当前请求可见的课题列表，重新核对有效成员关系 |

achievement 通过 indicator/topic 的公开服务取数，不跨模块引用 Mapper。单位启用状态使用 A 已有 SystemService。没有修改 A/C 模块、公共配置、POM、前端或任何迁移，本步无数据库结构变更。

## 2. 用户确认的累计及阶段口径

查询节点 N 纳入同项目 `sort_order <= N.sort_order` 的成果，使用排序值而不是节点 ID，保留此前停用节点的历史成果。分母只取 N 的 PUBLISHED 目标，不累加节点目标、不读草稿，也不自动回退到早期目标。

这是**按节点归属累计的当前事实**，不是截至节点截止日的历史报表。例如早期节点成果今天生效，查询早期节点也会看到已生效。响应 countingBasis 固定为 CUMULATIVE_NODE_CURRENT_FACTS。

| 阶段字段 | 判定证据 |
|---|---|
| initiated | 已创建，一条成果 ID 计一次 |
| preApproved | 曾有 ACHIEVEMENT / PRE_REVIEW / FINAL / APPROVED 审批记录 |
| external | 论文或专利曾成功登记投稿/申请，有对应成功动作记录 |
| formal | 曾有 FORMAL 提交快照；正式草稿不算正式提交 |
| supplement | 曾有 SUPPLEMENT 提交快照；待见刊/授权不算已提交补充 |
| effective | 当前 status=EFFECTIVE 且 counts_to_indicator=true，必须同时满足 |

历史证据使用 EXISTS，重提、多次审批不重复计数，退回不倒扣已到达阶段。阶段相互重叠，不能相加当成果总量。老数据缺少证据时不根据状态名称猜阶段；REPORT 历史不会混入。

## 3. 目标、比例和响应

- TOPIC 行使用课题目标；UNIT 行使用单位分配，允许单位承诺合计高于课题目标。
- 比例为生效数 × 100 ÷ 目标，保留两位小数、HALF_UP 四舍五入，可超过 100%。例如 2/1 为 200.00，2/3 为 66.67。
- 已下发零目标：targetQuantity=0、targetPublished=true、hasTarget=false、completionRate=null。
- 未下发：targetQuantity/targetVersion/completionRate 为 null，targetPublished/hasTarget 为 false；仍展示真实成果，不丢弃无目标成果。
- targetVersion 是该行生效发布版本，不是草稿版本。

| 响应字段 | 含义 |
|---|---|
| baseTotals | 五类生效数量，固定 PAPER/PATENT/COPYRIGHT/STANDARD/TALENT；排除历史单位和专项重复 |
| baseStages | 当前有效成员可见成果的六个阶段汇总 |
| rows | 基础指标明细，含 TOPIC/UNIT、目标、比例、阶段和 historical |
| specialIndicators | 专项明细，结构同 rows，可能交叠，不能加入基础总量 |
| nodeId、countingBasis | 所选节点和累计口径 |

**TOPIC 和 UNIT 是不同视角，不能将 rows 全部相加。** TOPIC 行 unitId=null；指定 unitId 或普通承担单位查询时，只返回 UNIT 行，避免部分单位分子对比全课题分母。

未下发的启用定义仍可有无目标行。停用基础定义、被当前目标引用的历史定义也保留可读性。本步不创建实际节点、指标或专项种子数据。

## 4. 数据范围与历史

- 管理角色全局；有效牵头读取本课题有效单位；承担单位仅本单位。HTTP 和 Java 查询同样校验当前身份，不能借 C 的调用越权。
- 停用成员或单位只向管理角色返回 historical=true 的单位行，消费方应单独展示；不计 TOPIC 当前汇总、baseTotals、baseStages。
- 停用单位账号不能查询；停用成员失去该课题范围；旧牵头失去其他单位范围，旧请求身份不能保留已撤销权限。
- 暂停、关闭、停用课题及停用节点仍可按权限查询历史。
- 显式课题/单位越权返回 403；不指定课题时，只汇总当前项目可见且匹配筛选条件的课题，无匹配返回空行与五类零数量。
- effectiveAchievements 也可能向管理角色返回历史引用，C 汇总当前完成量时必须排除 historical=true。

## 5. 专项规则

match_rule 只接受两个键，equals 必须是布尔 true，例如：

```json
{"field":"isChineseCoreJournal","equals":true}
```

| achievement_type | field 白名单 |
|---|---|
| PAPER | isChineseCoreJournal、isPowerGridFirstAuthor |
| PATENT | isPowerGridFirstApplicant |
| COPYRIGHT | isPowerGridFirstCompleter |

缺规则、类型错配、未知字段、额外键、equals 非布尔 true，返回 422 / INVALID_SPECIAL_INDICATOR_RULE，不执行脚本、SQL 或表达式。启用专项即使尚无目标也需正确配置。此示例用于 A 协调初始化，不要求用户直接修改部署库。

成果对应 detail 字段必须是真正布尔 true，字符串 "true" 不匹配。专项按当前详情分类，草稿分类可能随编辑改变；已生效记录由第 7 步锁定。一篇论文命中两个专项，基础 PAPER 仍只计一篇。

## 6. C 的调用契约

```java
private final IndicatorProgressQuery indicators;
private final AchievementProgressQuery achievements;

var targets = indicators.targets(topicId, nodeId, unitId);
var progress = achievements.progress(topicId, nodeId, unitId);
var effective = achievements.effectiveAchievements(topicId, nodeId, unitId);
```

调用必须已有真实 CurrentUser，禁止伪造管理角色。返回 record 与不可修改集合，不暴露实体/Mapper。Java 输入 ID 使用 long/Long；HTTP 及成果引用 ID 为字符串。Context.topicTargets 只在具备完整课题范围且未指定单位筛选时返回；单位不能获取其他单位目标。

统计在只读事务内读取目标、成员和事实，MySQL 默认 REPEATABLE READ 下形成一致视图。没有修改 C 的 dashboard/report/archive，也没有放开第 4/5 步降额限制。A/C 接入和契约评审仍需实际完成。

## 7. 验证结果

- Java 21 Maven **151 项通过，0 失败、0 错误、0 跳过**；新增统计集成测试 **20 项**。
- 真实 MySQL 8.4.3 + Spring Boot/MockMvc：节点排序累计、当前目标版本、草稿隔离、阶段去重、严格生效标记、四个专项、交叠去重、7 种非法规则、零/缺失目标、超额/舍入、单位权限、历史隔离、旧身份撤权、公开服务权限与不可修改集合、REPORT 隔离。
- 生效统计使用隔离库合成事实，不代表真实文件流程已跑通；生产统计服务本身不注入文件能力。
- 空库现有五条 Flyway 迁移成功，本步未新增迁移或修改部署数据库。
- OpenAPI 静态检查、新统计接口真实响应（含 null 比例）及此前全部真实响应回归通过。
- 前端测试 **94 项通过**，lint、build 均通过。测试存在既有的 getComputedStyle 伪元素提示，构建存在大包体积提示，均未导致失败。现有页面尚未接真实成果接口。

后端复验：Java 21，在 backend 运行 `.\mvnw.cmd test -B -ntp`。默认 Docker/Testcontainers；无 Docker 可使用 GZXM_TOPIC_TEST_MYSQL_URL 指向本机 gzxm_topic_test_* 隔离库，测试会清空夹具，不能指向部署库。

Maven 后使用已安装 openapi-spec-validator==0.7.2 的 Python 执行：

```powershell
python docs/collaboration/b-contracts/validate.py
python docs/collaboration/b-contracts/validate-achievement-progress-responses.py
```

## 8. 本地部署与验证

1. 在分支B使用原配置和 Java 21 重启后端，例如 `scripts/run-backend.ps1`；本步不用手工建表。准备真实课题/节点 ID 和单位登录令牌。
2. Postman 添加 Authorization: Bearer 本地令牌，GET `/api/v1/achievement-progress?nodeId=实际节点ID&topicId=实际课题ID`。
3. 创建无附件草稿，initiated 应加 1；提交预审不增加 preApproved，预审终审通过才增加。重复同一请求不多计；论文/专利登记投稿/申请后 external 加 1。
4. 承担单位只见自身 UNIT 行，牵头可见 TOPIC 和有效单位行。承担单位强行传其他 unitId，显式课题查询应 403。
5. 查询较晚节点，早期节点成果纳入累计；分母为较晚节点已下发目标。未下发时 targetPublished=false、completionRate=null，不用早期目标补空。
6. 零目标显示 targetQuantity=0、hasTarget=false、completionRate=null。若部署库没有真实生效成果，baseTotals=0 属正常结果，不应直接改数据库状态制造验收成功。
7. 停用成员后，管理角色看见 historical=true，但当前总量排除该单位；牵头不再看见其历史统计。专项配置错误返回 422，由 A 协调初始化。

**目前通过 HTTP JSON 验证，前端 Mock 页面数字不能证明真实统计已接通。** 前端接入属于第 9 步；正式成果真实创建仍受 A 文件能力限制。超额、专项交叠等完整数据场景的自动化证据来自隔离测试。
