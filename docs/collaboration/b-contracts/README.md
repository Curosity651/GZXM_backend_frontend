# B 第 1 步：业务、状态、字段和公共依赖契约评审包

日期：2026-09-15；分支：`分支B`；代码基线：`5217f6ac4c0bb69e9053585cd7fc0d3b7ec7a8da`。

## 1. 交付性质与权威来源

本包完成 B 的契约梳理、缺口分析、接口追踪和验收设计，不交付业务端点，不改变运行时行为。
状态：**评审包已编制；契约冻结及三人签署尚未完成**。自动化校验通过不代表业务评审通过。
本文不是第二份 OpenAPI；请求、响应和路径仍以原机器契约为准。下文明确标为“提案”的内容，未经评审不得作为新的实现要求。

必读来源：

1. [AGENTS.md](../../../AGENTS.md)：开发边界与强制检查。
2. [文档入口](../../README.md)。
3. [完整开发计划](../../development-plan.md)：业务口径、分工及冲突处理优先级。
4. [架构方案](../../architecture/backend-collaboration-plan.md)：模块协作及非功能要求。
5. [API 清单](../../api/api-reference.md)：动作使用者和可读接口说明。
6. [OpenAPI](../../api/openapi.yaml)：唯一机器契约。
7. [数据字典](../../database/mysql-data-dictionary.md)和[建表脚本](../../database/mysql-schema.sql)：字段、唯一约束和引用关系。
8. [基线评审要求](../../reviews/baseline-review.md)：进入 B/C 业务编码前应记录三人的评审结果。

发生冲突时按主计划第 17 节处理；不能机械地同时实施冲突的描述。旧前端 Mock 和历史方案只能作参考。

## 2. 范围及变更边界

- B 主责：`backend/src/main/java/com/gzxm/server/modules/{topic,indicator,achievement}`、对应测试、前端同业务功能。
- B 主责表：`biz_project`、`biz_topic`、`biz_topic_unit_membership`、`time_node`、`indicator_definition`、`topic_indicator`、`unit_indicator_allocation`、`achievement`、`achievement_material`。
- 共享表：`approval_record`、`submission_snapshot` 的成果部分由 B 使用，设计由 A 审核并与 C 协调。
- A 提供账号、当前用户、公共权限、单位服务、文件服务、审计、幂等等基础能力；C 提供报告、归档、工作台。
- 禁止跨模块直接使用 Mapper，也不能改用 JDBC/手写 SQL 读取对方表来绕过服务边界。
- 公共配置、POM、认证、公共 OpenAPI 模型、全局前端能力和基础设施不夹带进 B 业务修改。
- 新模块需要注册自己的 `repository` 包；现有应用入口的 MapperScan 变更列为基础协调项。
- 已合并 Flyway 迁移永不改写；新增迁移使用唯一 UTC 时间戳，由 A 审核顺序。
- 后续使用用户指定的“分支B”；不推送、合并或提交到 main。本包不改变仓库服务器侧分支保护设置。

## 3. 已明确的业务规则与验收编号

| 编号 | 现有规范要求 | 正向验收 | 拒绝/边界验收 |
|---|---|---|---|
| B-R01 | 一个重点项目，多个课题 | 课题和节点关联同一项目 | 拒绝不一致的课题/节点组合 |
| B-R02 | 五固定角色；LEAD/PARTICIPANT 是课题成员关系 | 同单位在不同课题身份不同 | 不因在甲课题牵头而可管理乙课题 |
| B-R03 | 科研助理创建/维护课题，唯一有效牵头 | 创建课题与牵头关系原子成功 | 编号重复、重复牵头或事务失败不留半条数据 |
| B-R04 | 当前牵头添加、启停承担关系 | 可添加有效单位 | 重复成员、跨课题 membershipId、停用牵头关系拒绝 |
| B-R05 | 角色动作、数据范围、业务状态共同授权 | 有动作权限且数据范围正确时允许 | 列表、详情、快照、修改均防 ID 越权 |
| B-R06 | 科研助理保存并直接下发课题指标，不增加审批 | 合法草稿发布 | 非科研助理即使误配动作权限也不能代为下发 |
| B-R07 | 节点是累计口径，后一节点目标不低于前一节点 | 中期 2、验收 5 | 中期 5、验收 2 拒绝；修改早期节点也需检查后续节点 |
| B-R08 | 牵头分配给全部成员，包含自己 | 分配合计等于或高于课题目标 | 合计低于课题目标不得发布 |
| B-R09 | 五类成果固定，专项是基础总量子集 | 一篇论文同时满足专项 | 基础总量不得因专项匹配加两次 |
| B-R10 | 成果属于课题和当前上传单位，不属于自筹项目 | 单位创建自己的成果 | 伪造单位、跨课题指标、错类型关联拒绝 |
| B-R11 | 预审、投递登记、正式提交沿同一成果记录推进 | 返回后修改原记录重提 | 不为每个阶段复制新成果计数 |
| B-R12 | 科研助理初审、技术负责人终审 | 对应人员处理当前环节 | 单位、管理员代审或越级审核拒绝 |
| B-R13 | 审批及快照留痕，重提产生新版本 | 旧版本可追溯 | 修改草稿不改变已提交快照 |
| B-R14 | 终审通过且满足统计条件才计完成数 | 单位与课题同时反映一项生效成果 | 草稿、预审通过、投递不能算完成；重试不重复计数 |
| B-R15 | 文件元数据通过 A 的文件能力复用 | 引用已完成上传的合法文件 | 未上传完成、已删除、无权使用文件拒绝 |
| B-R16 | 课题停用保留历史、限制业务写入 | 有权限用户能查看历史 | 停用时新增、提交、审批拒绝；恢复策略见 D02 |

例：课题目标 5，单位承诺分别为 2、2、2，允许超额分配；乙生效 1 项时，单位完成率 1/2，课题完成率 1/5。专项符合时单独展示专项 1，总量仍为 1。具体响应字段及零分母规则待 D08 确认。

## 4. B 的 24 个操作追踪表

下表的路径省略 `/api/v1`。角色要求来自 API 清单；权限码列依据当前初始化代码，为实现映射候选，不取代角色及数据范围判断。查询的细分页面权限待 D10 评审。

| operationId | 方法 | 路径 | 使用者/范围 | 权限码候选 | 规则 |
|---|---|---|---|---|---|
| listTopics | GET | /topics | 管理角色全部、单位有效成员课题 | 查询映射待 D10 | B-R02/B-R05 |
| createTopic | POST | /topics | 科研助理 | topic.manage | B-R01/B-R03 |
| getTopic | GET | /topics/{topicId} | 成员及管理角色 | 查询映射待 D10 | B-R05 |
| updateTopic | PUT | /topics/{topicId} | 科研助理 | topic.manage | B-R03/B-R16 |
| setTopicStatus | PUT | /topics/{topicId}/status | 科研助理 | topic.manage | B-R16 |
| listTopicMembers | GET | /topics/{topicId}/members | 成员及管理角色 | 查询映射待 D10 | B-R05 |
| addTopicParticipant | POST | /topics/{topicId}/members | 当前牵头单位 | topic-unit.manage | B-R04 |
| setTopicMembershipStatus | PUT | /topics/{topicId}/members/{membershipId}/status | 当前牵头；仅承担关系 | topic-unit.manage | B-R04 |
| listTimeNodes | GET | /time-nodes | 指标页面权限 | page:topic-indicator（待 D10） | B-R01 |
| listIndicatorDefinitions | GET | /indicator-definitions | 指标页面权限 | page:topic-indicator（待 D10） | B-R09 |
| listTopicIndicatorTargets | GET | /topics/{topicId}/indicator-targets | 成员及管理角色 | 查询映射待 D10 | B-R05/B-R07 |
| saveTopicIndicatorTargets | PUT | /topics/{topicId}/indicator-targets | 科研助理 | indicator.manage | B-R06/B-R07 |
| publishTopicIndicatorTargets | POST | /topics/{topicId}/indicator-targets:publish | 科研助理 | topic-indicator.publish | B-R06/B-R07 |
| listUnitAllocations | GET | /topics/{topicId}/unit-allocations | 牵头全课题、承担本单位；管理角色见 D10 | 查询映射待 D10 | B-R05/B-R08 |
| saveUnitAllocations | PUT | /topics/{topicId}/unit-allocations | 当前牵头单位 | unit-allocation.manage | B-R08 |
| publishUnitAllocations | POST | /topics/{topicId}/unit-allocations:publish | 当前牵头单位 | unit-allocation.publish | B-R08 |
| listAchievements | GET | /achievements | 单位本单位、牵头所牵头课题、管理角色全部 | 查询映射待 D10 | B-R05/B-R10 |
| createAchievement | POST | /achievements | 课题单位、当前单位归属 | achievement.submit | B-R10 |
| getAchievement | GET | /achievements/{achievementId} | 对象数据范围 | 查询映射待 D10 | B-R05/B-R15 |
| updateAchievement | PUT | /achievements/{achievementId} | 所属单位、可编辑状态 | achievement.submit | B-R10/B-R13 |
| executeAchievementAction | POST | /achievements/{achievementId}/actions | 所属单位、合法前置状态 | achievement.submit | B-R11/B-R13 |
| reviewAchievement | POST | /achievements/{achievementId}/reviews | 科研助理初审、技术负责人终审 | achievement.initial.approve / achievement.final.approve | B-R12/B-R14 |
| listAchievementSnapshots | GET | /achievements/{achievementId}/snapshots | 成果相关单位及审批角色 | 查询映射待 D10 | B-R05/B-R13 |
| getAchievementProgress | GET | /achievement-progress | 当前用户数据范围 | 查询映射待 D10 | B-R09/B-R14 |

### 4.1 通用协议

- ID：数据库 BIGINT UNSIGNED，JSON 字符串；Java 边界应明确支持范围，不能发生溢出后匹配错误记录。
- 日期 `YYYY-MM-DD`；时间 RFC 3339；分页 `page >= 1`、`1 <= size <= 200`。
- 错误：`application/problem+json`，包含 type/title/status/detail/instance/code，traceId/fieldErrors 按契约。
- 401：身份无效；403：动作或范围拒绝；409：唯一、状态或版本冲突；422：业务/字段校验。404 使用公共异常能力，但很多操作没有显式列入契约响应，需要 D13 统一。
- `Idempotency-Key`：8—100 字符，当前四类 POST（两级发布、成果动作、成果审批）必传。
- 审批请求 decision 是 `APPROVE/RETURN`，审批记录返回 `APPROVED/RETURNED`，不要直接复用枚举。
- 当前发布成功返回 204；创建成果/课题/成员及审批成功返回 201；不能统一改成 200。

## 5. 字段与数据库映射

“可选”指当前 OpenAPI 未列 required，不意味着业务提交阶段可以任意缺失。业务条件必填需要在 D04/D05 中明确后同步契约。

| 请求/对象 | 当前必填及重要字段 | 数据映射与限制 | 缺口 |
|---|---|---|---|
| TopicWriteRequest | 必填 code/name/leadUnitId；可选 summary/participantUnitIds/startDate/endDate/recordVersion | biz_topic.code 64、name 300；project_id/created_by/updated_by 由服务端提供；牵头关系同步保存 | 创建默认状态、换牵头、participantUnitIds 更新语义见 D01 |
| 课题状态请求 | enabled 必填、status 可选 | enabled 与 DRAFT/ACTIVE/PAUSED/CLOSED 分开 | 合法组合、恢复、状态版本见 D02/D06 |
| 成员请求 | 新增 unitId；状态 enabled | 关系唯一键 topic_id+unit_id；有效牵头唯一键 | 停用后重加是冲突还是恢复见 D01 |
| TimeNode | 响应 id/name/deadline/sortOrder/enabled | time_node 另有 project_id/code；项目内 code、sort_order 唯一 | 只有 GET，初始化数据由 D09 协调 |
| IndicatorDefinition | id/code/name/achievementType/unit/category | unit → unit_name；category BASE/SPECIAL；match_rule 不对前端暴露 | 专项目录与匹配规则见 D09 |
| IndicatorTargetBatch | nodeId/targets；每行 indicatorDefinitionId/targetQuantity | topic_indicator；targetQuantity 非负整数；topic+node+definition 唯一 | 空批次、替换/合并、版本及草稿见 D06 |
| UnitAllocationBatch | nodeId/allocations；每行 unitId/indicatorDefinitionId/targetQuantity | unit_indicator_allocation；membership_id/topic_indicator_id 服务端解析并验证；topic+unit+node+definition 唯一 | 缺少匹配目标拒绝；草稿和调整细节见 D06 |
| AchievementWriteRequest | topicId/nodeId/indicatorDefinitionId/title/responsiblePerson；可选 detail/materialFileIds/recordVersion | title 500、responsible_person 100；detail → detail_json；unit_id/membership_id/project_id 由服务端解析；achievementType 从定义确定 | 禁止信任多余 unitId；五类详情和绑定指标条件见 D04 |
| AchievementActionRequest | action 必填；externalSubmissionDate/Number 可选 | 日期/编号可存详情，但约定键需冻结 | 缺少动作版本、投递条件字段及补充规则见 D03/D06 |
| ReviewRequest | decision 必填；opinion/submittedVersion 可选 | approval_record.opinion 1000；角色和记录决定 stage/level，不能让请求自行指定审批级别 | 退回意见必填、submittedVersion 必填策略见 D03/D06 |
| Achievement 响应 | id/topicId/unitId/nodeId/indicatorDefinitionId/achievementType/title/status/recordVersion/submittedVersion/materials 必填 | countsToIndicator 对应 counts_to_indicator；materials 为 FileObject 数组 | status 无枚举；没有审批记录字段和材料类别见 D03/D05/D07 |
| SubmissionSnapshot | businessType/businessId/stage/submittedVersion/submitterId/payload 等 | payload_json 应含提交时业务和文件元数据；business_type+business_id+submitted_version 唯一 | 版本不可覆盖；共享访问契约见 D11 |
| AchievementProgress | baseTotals/specialIndicators/rows | baseTotals 值类型为 integer；专项和行是开放对象 | 完成率放 rows，字段语义待 D08，不擅自向整数集合放百分比 |

### 5.1 五类详情的评审范围（提案，不是当前已冻结字段）

| 类型 | 需确认的详情分组 | 需确认的正式认定材料 |
|---|---|---|
| PAPER | 论文名、作者/单位排序、期刊会议、检索、项目标注、投递录用发表日期 | 录用、正式论文、检索证明的适用条件 |
| PATENT | 提案、发明人/申请人排序、专利类型、申请/受理/授权信息 | 受理是否可认定基础数量，授权补充是否改变专项 |
| COPYRIGHT | 软件名/版本、著作权人、技术特点、登记信息 | 证书、鉴别材料等按阶段必需性 |
| STANDARD | 标准类别、参与排序、送审/立项/发布信息 | 送审稿或发布证明等认定条件 |
| TALENT | 培养对象、类别、培养/完成日期 | 学位论文、答辩或毕业证明适用条件 |

不从旧截图推断必填字段；截图不在本仓库时不宣称已核实。预审不强制论文原文和源代码是当前数据字典明确要求。

## 6. 状态与动作

### 6.1 已有机器契约

- 课题：`DRAFT/ACTIVE/PAUSED/CLOSED`，另有 enabled。
- 指标：`DRAFT/PUBLISHED`，响应 version 对应发布版本；它不天然等于编辑乐观锁。
- 成果：status 仅定义为 string，**尚无完整机器状态枚举**。
- 成果动作：`SUBMIT_PRE_REVIEW`、`REGISTER_EXTERNAL_SUBMISSION`、`START_FORMAL`、`SUBMIT_FORMAL`、`SUBMIT_SUPPLEMENT`。
- 文件：`PENDING/READY/DELETED`；业务只能关联符合权限和状态要求的对象。

### 6.2 成果流转评审表（语义提案，待 D03）

以下不新增英文状态枚举，避免把未评审名字固化到代码。

| 当前阶段 | 动作 | 执行者 | 下一阶段 | 版本/计数 |
|---|---|---|---|---|
| 预审草稿/预审退回 | SUBMIT_PRE_REVIEW | 所属单位 | 预审初审中 | 新提交快照；不计数 |
| 预审初审中 | APPROVE / RETURN | 科研助理 | 预审终审中 / 预审退回 | 审核同一 submittedVersion |
| 预审终审中 | APPROVE / RETURN | 技术负责人 | 预审通过 / 预审退回 | 不计数 |
| 预审通过 | REGISTER_EXTERNAL_SUBMISSION | 所属单位 | 已投稿/申请 | 需投递日期/编号规则；不计数 |
| 已投稿/申请 | START_FORMAL | 所属单位 | 正式草稿 | 不另建成果；不计数 |
| 正式草稿/正式退回 | SUBMIT_FORMAL | 所属单位 | 正式初审中 | 新快照；不计数 |
| 正式初审中 | APPROVE / RETURN | 科研助理 | 正式终审中 / 正式初审退回 | 不计数 |
| 正式终审中 | APPROVE / RETURN | 技术负责人 | 已生效 / 正式终审退回 | 通过且满足条件才计一次 |
| 已生效或其他指定状态 | SUBMIT_SUPPLEMENT | 所属单位 | 待业务确定 | 不得未经定义重复认定或清除已有计数 |

需确认：人才是否直接正式草稿；预审是否也明确两级；正式退回重提是否总回初审；审批中/生效后的可编辑字段；补充审核对原计数和专项的影响。
无合法转换的动作应拒绝且不产生快照/审批/计数副作用。最终状态机需同时校验 role、action authority、scope、status、submittedVersion。

## 7. 公共依赖盘点与提供方责任

以下“现状”来自当前源码，不表示已完成真实数据库、文件或 Redis 全链路联调。

| 依赖 | 当前可见实现 | B 的使用方式/缺口 | 责任及退出条件 |
|---|---|---|---|
| 当前用户 | SecurityContextFacade.requireCurrentUser；CurrentUser 含 id/unitId/roleCode/authorities/memberships | 使用公共上下文，不复制认证；A 当前每次 loadCurrentUser 查询有效成员 | A 提供，B 验证启停后的范围行为 |
| TopicAccessService | requireMember/requireLead/requireInternalUnit/requireOwnedByCurrentUnit | 对全局角色放行；不检查课题是否 ACTIVE；动作角色仍由 B 显式限制 | D02/D10；不能认为 requireLead 等价于“单位可写” |
| 单位查询 | SystemService.listUnits(keyword, internal) 返回 id/code/name/internal/enabled | 可通过公开应用服务查单位并检查 enabled；没有按 ID 批量查询契约 | A/B 评审稳定查询接口；B 不引用 UnitMapper |
| 文件服务 | FileService.createTicket/complete/signedUrl；元数据读取与权限方法是 private | 缺少公开的批量 READY/归属校验及元数据查询；当前下载仅全局角色或上传者可读，牵头查看其他单位材料不支持 | D05：A 提供文件查询/关联校验和业务授权接入，B 提供成果可读判断 |
| 审计 | AuditService.success，REQUIRES_NEW，只记录成功 | 在外层事务中提前记录可能业务回滚而成功审计已提交 | D12：A 约定提交后审计及失败记录方式 |
| 幂等 | api_idempotency 表存在，源码未发现公共幂等应用服务 | B 不自行读取该公共表；需请求摘要/冲突/并发/响应重放契约 | D06：A 提供，B 做动作集成测试 |
| 审批与快照 | 两张共享表已设计，暂无公开持久化服务 | B 只实现成果部分，与 C 区分 businessType 和 stage | D11：A/B/C 确认访问层位置、版本唯一性及事务 |
| Mapper 扫描 | GzxmApplication 当前只扫描 audit/system/file | 新业务 repository 尚未注册 | D12：基础任务精准增加 B 的 repository 包 |

### 7.1 B 向 C 提供的查询契约提案（第 3/8 步实现）

第 3 步已提供基本信息、成员和牵头事实查询接口，签名及行为见 [公开课题查询契约](topic-step3.md)；以下指标和进度查询仍待第 8 步。A/C 的接入评审状态不因 B 完成实现而自动变更。
| 服务意图 | 输入 | 最小输出 | 行为约束 |
|---|---|---|---|
| 课题基本信息 | topicId | id/projectId/code/name/leadUnitId/status/enabled | 不返回 Mapper 实体；不存在与无权访问按公共错误规则 |
| 课题成员清单 | topicId、是否包含停用（受权调用） | membershipId/topicId/unitId/type/enabled | 普通请求不能通过 includeDisabled 绕过范围限制 |
| 牵头事实查询 | topicId/unitId | 有效牵头判断 | 返回身份事实，不替代 report/archive 的动作授权 |
| 指标目标 | topicId/nodeId、可选 unitId | 当前有效课题/单位目标及版本 | 草稿与发布口径一致，避免调用方重复计算 |
| 生效成果和进度 | topicId/nodeId、可选 unitId | 基础/专项/阶段数量及计量口径 | 按数据范围过滤，累计不重复；字段依 D08 |

第 3 步分别提供 TopicQueryService（当前用户数据范围）与 TopicIdentityFacts（可信身份加载），避免认证加载反向依赖尚未建立的 CurrentUser。实现可注入 Bean，未改动 A 的现有认证加载流程。

## 8. 决策、冲突和基础任务清单

| 编号 | 问题与依据 | 建议处理（未确认的不视为冻结） | 责任人 | 阻塞步骤 |
|---|---|---|---|---|
| D01 | TopicWriteRequest 可传 leadUnitId/participantUnitIds；更换牵头及更新成员语义不明 | 明确原子换牵头、旧牵头身份、成员列表替换/增量、重复停用重加、创建默认状态 | B 提案，A 审，影响 C 时 C 审 | 2 |
| D02 | enabled+status 双状态；停用历史可读与有效成员列表过滤存在边界 | 明确读历史的人、PAUSED/CLOSED 恢复和写权限；禁用账号仍不可登录 | A/B/C | 2、3 |
| D03 | 成果 status 无枚举，SUBMIT_SUPPLEMENT 未定，预审级别仅旧前端详细描述 | 评审第 6 节，补齐阶段/状态/动作、退回和人才分支，再同步 OpenAPI | B 提案，A/C 和业务确认 | 6、7 |
| D04 | 用户确认：仅有效成员、绑定本单位已下发基础指标（允许零），创建后归属不可更改；五类 detail 按现有表单，草稿允许缺项 | 第 6 步落实类型/枚举/长度/日期和 recordVersion 校验；正式提交必填留第 7 步 | 用户业务确认已收到；A/C 评审未完成 | 6、7 |
| D05 | 用户确认结构化材料、同类多文件、保留版本；同时明确保持 B 边界等待 A | B 预留材料集合及历史结构；非空附件暂返回 503；A 按文件基础提案提供公开能力，再做真实联调 | A/B，共享文件 C 审；当前未接通 | 6、7 |
| D06 | 通用 recordVersion 约定与批量指标、状态动作请求不一致；无幂等公共服务 | 定义批次替换语义、空批次、草稿与发布共存、调整历史、已完成下限、提交版本及幂等重放；新迁移只追加 | A/B，公共模型 C 审 | 4、5、7 |
| D07 | 可读文档成果详情含审批记录，Achievement schema 没该字段 | 评审向详情新增可选 approvals 或独立读取接口，不挪用 snapshots 当审批记录 | B 提案，A/C 审 | 6、7 |
| D08 | 进度 rows/专项开放，阶段统计和零目标规则不明确 | 明确当前状态数与历史到达数、节点归属或认定日期口径、分母、超额及零目标、专项交叠 | B/C 与业务 | 8、9 |
| D09 | 节点/指标只有 GET，缺固定项目/节点/专项规则的初始化清单 | B 提出非敏感初始化数据，A 审核新迁移；不擅增配置端点或第六类成果 | A/B 与业务 | 2、4、5 |
| D10 | 初始化技术负责人有 topic.manage/indicator.manage/topic-indicator.publish；查询权限不细化 | API 清单限定科研助理写；B 必须同时做角色约束；A 协调种子权限修正，确认查询页面与管理角色分配可读范围 | A/B，页面公共变更 C 审 | 2—9 |
| D11 | approval_record/submission_snapshot 共享，无统一访问层约定 | 明确成果/报告各自使用范围、共享 DTO、同事务和不可变性，不在 common 放业务状态机 | A/B/C | 7 |
| D12 | MapperScan 未含 B；审计成功独立事务存在时序风险 | 分别建立精准注册、提交后审计基础任务；不通过扫描全模块或复制审计解决 | A 主责，B/C 审 | 2 及所有写入 |
| D13 | 若干接口未列 401/403/404/409/422 等实际错误；必填版本等不足 | 列出逐操作错误响应及稳定 code，经评审同步契约；不擅改公共 Problem | A 协调，B/C | 2—9 |

已可按权威顺序处理的差异：实际后端 `backend/` 对应旧文档 `server/`；迁移使用主计划及 AGENTS 的时间戳，非旧 V1_1xx；课题指标写入按主计划/API 限科研助理；用户指定“分支B”优先于文档分支命名示例。以上不需要重建工程或改动 main。

错误码提案（未新增到公共代码）：TOPIC_NOT_OPERATIONAL、TOPIC_CODE_CONFLICT、TOPIC_MEMBER_CONFLICT、INDICATOR_CUMULATIVE_INVALID、ALLOCATION_TOTAL_TOO_LOW、ACHIEVEMENT_STATE_CONFLICT、SUBMITTED_VERSION_CONFLICT。已有公共代码如 TOPIC_SCOPE_DENIED/UNIT_SCOPE_DENIED/FILE_NOT_READY 应优先复用，统一由 D13 评审。

### 8.1 评审记录

- 2026-09-16 第 5 步：B 沿用第 4 步的草稿/生效/历史分离，实现单位分配的牵头授权、单位范围、覆盖/合计/累计校验及目标版本绑定。降额按已生效值作保守下限；实际完成量调整契约仍未实现，A/C 评审未代签。见 [第 5 步验收](topic-step5.md)。
- 2026-09-16 第 4 步：用户确认 D06 的节点完整替换草稿、清空和保留发布历史方案；B 实现草稿版本校验及发布历史内持久化去重，不访问 A 公共幂等表。降额下发、公共能力统一接入及 D09 实际初始化目录仍待后续落实；新迁移和接口变更未代签 A/C 评审。见 [第 4 步验收](topic-step4.md)。
- 2026-09-15 第 2 步更新：用户已确认 D01 的成员增量/原子换牵头规则、D02 的课题只读与恢复/成员可见范围，以及 D09 的唯一有效项目约束。D09 的节点/指标初始化仍待后续处理。课题范围 D10 通过角色与动作权限双检查落实；D12 采用模块内精准 MapperScan 和事务返回后调用公共审计；D13 已补 Topics 操作错误响应。详见 [第 2 步实现与验收](topic-step2.md)。这些实现不代表 A/C 已批准公共基础改造，其余模块决策保持待确认。
- B：本包已编制，静态验证见 [验证记录](validation.md)。
- A：待确认公共权限、单位/文件/幂等/审计、数据库和扫描依赖。
- C：待确认课题查询、共享审批快照及统计字段。
- 业务负责人：待确认 D01—D09 中无法由现有规范唯一推出的业务选项。
- 本包没有伪造 A/C 签署；后续应在任务或 PR 中记录决策、审查人和日期。

## 9. 后续步骤完成定义

| 步骤 | 对应规则 | 必须的证据 |
|---|---|---|
| 1 契约梳理 | 全部 | 本包、24 操作映射、静态检查、缺口及真实测试记录；冻结另需评审 |
| 2 课题成员 | B-R01—05/16 | Service/API 正反用例、MySQL 唯一约束与原子性 |
| 3 公开查询 | B-R02/05/16 | C 的 Stub/契约用例，跨课题/停用范围 |
| 4 课题指标 | B-R06/07/09 | 累计、重复维度、非法节点、发布及重试 |
| 5 单位分配 | B-R07/08/09 | 合计、成员范围、调整下限、草稿/发布 |
| 6 成果材料 | B-R10/11/15 | 五类字段、本人归属、材料权限与版本 |
| 7 审批快照 | B-R11—14 | 所有允许/禁止流转、两角色审批、重提和幂等并发 |
| 8 统计服务 | B-R07/09/14 | 累计、基础/专项、零目标、超额、数据范围 |
| 9 联调 | 全部 | 前端真实 API、五角色操作、刷新持久化及跨模块查询一致 |

每项权限动作至少允许和拒绝各一例；必须覆盖 401/403、参数错误、直接 API 越权、并发与重复请求。关键 SQL 用 MySQL/Testcontainers，不能用 H2 替代。
每步按仓库规定执行 Java 21 Maven 测试、前端 test/lint/build、契约检查；失败或未运行不能计作通过。共享评审未完成不能标记“已批准”。

## 10. 如何在本地验证本步骤

本步骤没有新增页面或业务 API，**不能通过点击部署页面直观看到新功能**。现有 Mock 页面可演示旧原型，但不是本步骤的后端实现证据；运行时 Swagger 只反映已实现 Controller，不能据此认为 67 个设计操作均已实现。

1. 在“分支B”打开本文，对照 operationId、字段与 D01—D13 逐项评审。
2. 从仓库根目录执行下列静态检查；它会验证 OpenAPI 3.1、所有引用、B 的 24 个操作映射和正文中的数据库字段引用，不连接数据库或改写文件。

```powershell
python -m venv "$env:TEMP\gzxm-b-contract-check"
& "$env:TEMP\gzxm-b-contract-check\Scripts\python.exe" -m pip install openapi-spec-validator==0.7.2
& "$env:TEMP\gzxm-b-contract-check\Scripts\python.exe" docs/collaboration/b-contracts/validate.py
```

3. 在 backend 执行 `java -version`（必须 Java 21）、`.\mvnw.cmd test`；在 frontend 执行 `npm test`、`npm run lint`、`npm run build`。本机默认 Node 20.16 不兼容当前依赖，应使用满足 Vite/jsdom engines 的版本；本次使用 Node 24.19.0。
4. 查看 [验证记录](validation.md) 中测试覆盖边界；本步骤不执行建表 SQL、不改变部署数据库，不需要为文档重启服务。
5. 后续第 2 步有真实课题 API 后再开展“科研助理创建 → 牵头添加成员 → 越权拒绝”的交互验收。
