# 重点项目科研管理系统 API 接口清单

> 本文是供产品、前端和后端共同评审的可读版接口文档。接口统一前缀为 `/api/v1`，完整的请求字段、响应结构和可生成代码的机器契约仍保存在 [`openapi.yaml`](./openapi.yaml)。

## 1. 通用约定

| 项目 | 约定 |
|---|---|
| 传输协议 | 生产环境使用 HTTPS |
| 数据格式 | 请求和响应默认使用 `application/json` |
| 身份认证 | 登录以外的接口使用 `Authorization: Bearer <accessToken>` |
| ID格式 | 数据库使用 `BIGINT`，JSON 中统一以字符串返回 |
| 时间格式 | 日期使用 `YYYY-MM-DD`，时间使用 RFC 3339 |
| 分页参数 | `page` 从 1 开始，`size` 为每页数量 |
| 错误格式 | 统一返回 `application/problem+json` |
| 并发控制 | 更新数据时携带 `recordVersion`，版本不一致返回 `409` |
| 重复提交 | 提交、审批等关键操作携带 `Idempotency-Key` |
| 数据权限 | 先校验角色动作权限，再校验课题关系、单位属性和数据归属 |
| 归档审批 | 国家材料和自筹材料均不审批，上传成功即计入归档进度 |

## 2. 登录与会话

| 方法 | 地址 | 接口用途 | 使用者 |
|---|---|---|---|
| POST | `/auth/login` | 用户名、密码登录，返回访问令牌和刷新令牌 | 未登录用户 |
| POST | `/auth/refresh` | 使用刷新令牌换取新的访问令牌 | 已登录用户 |
| POST | `/auth/logout` | 退出当前会话并使令牌失效 | 已登录用户 |
| GET | `/auth/me` | 获取当前用户、角色、权限、所属单位及课题关系 | 已登录用户 |

## 3. 用户、角色和单位

| 方法 | 地址 | 接口用途 | 使用者/限制 |
|---|---|---|---|
| GET | `/users` | 按用户名、角色、状态分页查询用户 | 系统管理员 |
| POST | `/users` | 创建用户并指定角色、联系人信息和登录密码；课题单位账号由后端按用户名自动建立同名单位 | 系统管理员；单位内外属性由角色决定，同一单位只能有一个有效课题单位账号 |
| GET | `/users/{userId}` | 查看用户详情 | 系统管理员 |
| PATCH | `/users/{userId}` | 修改用户名、联系人、手机和邮箱 | 系统管理员；不允许在编辑时修改角色 |
| PUT | `/users/{userId}/status` | 启用或停用账号 | 系统管理员；不物理删除账号 |
| PUT | `/users/{userId}/password` | 管理员指定新密码，并使该账号旧会话失效 | 系统管理员 |
| GET | `/roles` | 查询五类固定角色 | 系统管理员 |
| GET | `/roles/{roleId}` | 查询角色及其权限 | 系统管理员 |
| PUT | `/roles/{roleId}` | 保存角色权限 | 系统管理员；外部单位的自筹权限不可开启 |
| GET | `/permissions` | 查询页面和操作权限目录 | 系统管理员 |
| GET | `/units` | 按名称、内外部属性查询单位 | 系统管理员、科研助理和课题牵头单位 |

## 4. 课题与成员关系

| 方法 | 地址 | 接口用途 | 使用者/限制 |
|---|---|---|---|
| GET | `/topics` | 查询当前用户有权访问的课题 | 全部角色；结果按数据范围过滤 |
| POST | `/topics` | 创建课题并指定唯一牵头单位 | 科研助理 |
| GET | `/topics/{topicId}` | 查看课题基本信息 | 课题成员、科研助理、技术负责人、管理员 |
| PUT | `/topics/{topicId}` | 修改课题基本信息 | 科研助理 |
| PUT | `/topics/{topicId}/status` | 启用、暂停或关闭课题 | 科研助理 |
| GET | `/topics/{topicId}/members` | 查看课题牵头和承担单位 | 课题成员及管理角色 |
| POST | `/topics/{topicId}/members` | 为课题添加承担单位 | 当前课题牵头单位 |
| PUT | `/topics/{topicId}/members/{membershipId}/status` | 启用或停用承担关系 | 当前课题牵头单位；牵头关系不可由此停用 |

课题模块的第2步实现细则（用户于2026-09-15确认，字段及路径保持兼容）：

- 创建默认进行中且启用；数据库必须且仅有一个有效重点项目，否则返回409 `PROJECT_CONFIGURATION_REQUIRED`，不会自动生成项目。
- 编辑课题必须携带当前 `recordVersion`；不一致或缺失返回409。`participantUnitIds`只补充新关系，省略/空数组不移除任何成员，已有停用关系不会自动恢复。
- 换牵头在同一事务内完成；旧牵头保留为承担单位，新牵头已有停用关系会恢复有效。承担列表不得重复或包含新牵头。
- 暂停、关闭、停用后禁止普通编辑、成员新增和成员启停；科研助理仍可用课题状态接口恢复。状态的enabled与status是独立字段，省略status保持原值。
- 成员新增重复返回409，停用成员通过状态接口恢复。普通成员停用后不再拥有该课题读取范围；管理角色可查看历史。
- 所有写入会更新课题版本。状态和成员接口当前没有客户端版本参数，仅按课题行锁串行执行，不宣称能拒绝所有旧页面覆盖。
- 格式/日期/分页错误返回422，范围或角色拒绝403，课题或课题内成员不存在404，重复、不可写状态、版本冲突409；错误体沿用公共Problem结构。
- 查询允许五类已登录角色，按数据范围限制；写动作同时校验动作权限和业务角色，技术负责人或管理员即使被误配topic.manage也不能代科研助理写课题。

## 5. 科研指标


| 方法 | 地址 | 接口用途 | 使用者/限制 |
|---|---|---|---|
| GET | `/time-nodes` | 查询项目考核时间节点 | 有指标页面权限的用户 |
| GET | `/indicator-definitions` | 查询五类成果指标和专项指标定义 | 有指标页面权限的用户 |
| GET | `/topics/{topicId}/indicator-targets` | 按时间节点查看课题累计指标 | 当前课题成员及管理角色 |
| PUT | `/topics/{topicId}/indicator-targets` | 保存课题累计指标草稿 | 科研助理 |
| POST | `/topics/{topicId}/indicator-targets:publish` | 正式下发课题累计指标 | 科研助理 |
| GET | `/topics/{topicId}/unit-allocations` | 按时间节点查看单位指标分配 | 牵头单位看全课题，承担单位看本单位 |
| PUT | `/topics/{topicId}/unit-allocations` | 保存各单位累计指标分配 | 当前课题牵头单位 |
| POST | `/topics/{topicId}/unit-allocations:publish` | 正式下发单位指标 | 当前课题牵头单位；单位合计不得低于课题要求 |

指标目录补充：`/time-nodes` 与 `/indicator-definitions` 检查 `page:topic-indicator` 权限，只返回启用目录项。节点取唯一有效项目并按 `sortOrder/id` 排序；项目配置不唯一返回 409。目录尚未初始化返回空数组，不自动创建业务配置。

课题指标第 4 步：GET 默认读取生效目标；`view=draft` 仅限有 `indicator.manage` 的科研助理，并返回 `X-Draft-Version`。PUT 完整替换草稿，首次版本 0，后续 `draftVersion` 必须匹配；空数组清空草稿，不影响生效数据。发布必须携带 `draftVersion` 与 `Idempotency-Key`，保留历史；同键同请求重放 204，不同请求 409。数量非负、节点累计不递减、专项不超过同类基础。已发布目标的删除或降额草稿暂不能下发，须后续接入完成量与分配调整约束。见 [验收说明](../collaboration/b-contracts/topic-step4.md)。

单位分配第 5 步：GET 默认读生效值，管理角色/当前牵头看全课题，承担单位只看本单位；`view=draft` 仅具有维护权限的当前牵头可读。PUT 按节点完整替换草稿，`draftVersion` 做编辑版本校验；发布须提交版本与幂等键。发布要求覆盖全部有效启用成员（含牵头）及全部生效指标，每项合计不得低于课题要求，允许超额，零分配显式填 0。课题指标版本变化后须重新核对保存；当前不能下发降低有效成员既有分配的草稿。详见 [第 5 步验收](../collaboration/b-contracts/topic-step5.md)。

## 6. 成果管理

第 7 步已实现列表、创建、详情、编辑、动作、审批、快照七个接口。五类均先两级预审，论文/专利再经正式及补充两级审批后计数；其他三类正式终审生效。编辑仅限草稿、退回和待补充；动作携带 recordVersion，审批携带 recordVersion + submittedVersion，并使用 Idempotency-Key。详情含 approvals，pendingForMe 按当前审批级别过滤。正式/补充提交及非空附件仍等待 A 的文件公开能力，未接入返回 503 并回滚；已有当前材料的记录查询也受限。详见[第 7 步验收说明](../collaboration/b-contracts/achievement-step7.md)。



| 方法 | 地址 | 接口用途 | 使用者/限制 |
|---|---|---|---|
| GET | `/achievements` | 按课题、单位、类型和状态查询成果 | 单位看本单位；牵头单位看所牵头课题；管理角色看全部 |
| POST | `/achievements` | 新建成果记录 | 课题单位；成果归属当前登录单位 |
| GET | `/achievements/{achievementId}` | 查看成果表单、材料和审批记录 | 对该成果有数据权限的用户 |
| PUT | `/achievements/{achievementId}` | 修改成果草稿或被退回记录 | 成果所属单位 |
| POST | `/achievements/{achievementId}/actions` | 发起预审、登记投稿、提交正式成果等状态动作 | 成果所属单位 |
| POST | `/achievements/{achievementId}/reviews` | 初审或终审成果，可通过或退回 | 科研助理初审；项目技术负责人终审 |
| GET | `/achievements/{achievementId}/snapshots` | 查看历次提交版本快照 | 成果相关单位和审批角色 |
| GET | `/achievement-progress` | 统计指标、发起、预审、投稿、正式成果及完成率 | 按当前用户课题数据范围返回 |

第 8 步已实现 `/achievement-progress`：nodeId 必填，topicId/unitId 可选。按节点排序累计当前事实，阶段按历史证据去重；分母只取查询节点已下发目标。零/未下发目标完成率 null，可超过 100%。TOPIC/UNIT 为不同视角，不能相加；historical 行只向管理角色展示且不计当前总量。专项不加进 baseTotals，非法 match_rule 返回 422。字段与 C 的公开服务见[第 8 步说明](../collaboration/b-contracts/achievement-step8.md)。

## 7. 月报和季报

| 方法 | 地址 | 接口用途 | 使用者/限制 |
|---|---|---|---|
| GET | `/topics/{topicId}/report-rule` | 查看该课题年度月季报规则 | 当前课题成员及管理角色 |
| PUT | `/topics/{topicId}/report-rule` | 配置启用类型、开放日、截止日和季度月份 | 科研助理；每个课题单独配置 |
| GET | `/reports` | 查询已经创建或填报的月季报 | 按课题数据范围过滤；不自动生成未填报记录 |
| POST | `/reports` | 创建某一期月报或季报 | 当前课题牵头单位；每课题每期仅一份 |
| GET | `/reports/{reportId}` | 查看月季报详情及审批状态 | 当前课题成员及管理角色 |
| PUT | `/reports/{reportId}` | 保存月季报内容 | 当前课题牵头单位 |
| POST | `/reports/{reportId}:submit` | 提交月季报初审 | 当前课题牵头单位 |
| POST | `/reports/{reportId}/reviews` | 初审或终审，可通过或退回 | 科研助理初审；项目技术负责人终审 |
| GET | `/reports/{reportId}/snapshots` | 查看月季报历次提交版本 | 当前课题成员和审批角色 |
| GET | `/report-progress` | 按课题和期间统计已提交、审批中、通过和逾期情况 | 科研助理、项目技术负责人 |

## 8. 国家归档材料

| 方法 | 地址 | 接口用途 | 使用者/限制 |
|---|---|---|---|
| GET | `/archive/national` | 查询“课题—单位”国家材料目录入口 | 课题单位及管理角色；按课题关系过滤 |
| GET | `/archive/national/topics/{topicId}/units/{unitId}/folders` | 查看指定课题单位的国家材料文件夹 | 单位看本单位；牵头单位可查看所牵头课题全部单位 |
| POST | `/archive/national/topics/{topicId}/units/{unitId}/folders` | 新建自定义国家材料文件夹 | 只能为本单位清单新增 |
| DELETE | `/archive/folders/{folderId}` | 删除自定义文件夹 | 仅创建单位；预置文件夹不可删除 |
| GET | `/archive/folders/{folderId}/files` | 查看文件夹中的文件 | 对该文件夹有查看权限的用户 |
| POST | `/archive/folders/{folderId}/files` | 将已经上传的文件放入文件夹 | 只能操作本单位文件夹 |
| DELETE | `/archive/folders/{folderId}/files/{fileId}` | 从文件夹删除文件 | 只能操作本单位文件夹；无审批流程 |

## 9. 配套自筹材料

| 方法 | 地址 | 接口用途 | 使用者/限制 |
|---|---|---|---|
| GET | `/self-funded-projects` | 按课题、单位查询配套自筹项目 | 仅内部单位及管理角色；外部单位禁止访问 |
| POST | `/self-funded-projects` | 在相关课题下新建本单位自筹项目 | 内部课题单位 |
| GET | `/self-funded-projects/{projectId}` | 查看自筹项目详情 | 项目所属内部单位；内部牵头单位可查看所牵头课题全部项目 |
| PUT | `/self-funded-projects/{projectId}` | 修改自筹项目基本信息 | 仅项目所属单位 |
| GET | `/self-funded-projects/{projectId}/folders` | 查看自筹项目材料文件夹 | 项目所属单位及有权查看的内部牵头单位 |
| GET | `/archive-progress` | 汇总国家材料和自筹材料归档进度 | 科研助理、项目技术负责人 |

自筹文件夹中的上传、查看和删除复用国家材料的通用文件夹接口，不再建立一套重复接口。

## 10. 文件服务

| 方法 | 地址 | 接口用途 | 使用者/限制 |
|---|---|---|---|
| POST | `/files/upload-tickets` | 申请上传凭证和目标对象地址 | 已登录用户；先校验对应业务数据权限 |
| POST | `/files/{fileId}:complete` | 通知后端上传完成并登记文件元数据 | 上传发起人 |
| GET | `/files/{fileId}/download-url` | 获取短期有效下载地址 | 对关联业务记录有查看权限的用户 |
| GET | `/files/{fileId}/preview-url` | 获取短期有效预览地址 | 对关联业务记录有查看权限的用户 |

文件服务不绑定MinIO。开发初期可以使用Mock或本地实现，确定对象存储后再切换为MinIO/S3适配器。

## 11. 工作台

| 方法 | 地址 | 接口用途 | 使用者/限制 |
|---|---|---|---|
| GET | `/dashboard/summary` | 返回当前用户可见课题、待办审批、成果、月季报和归档统计 | 已登录用户；按角色和课题范围生成 |

## 12. 常用状态说明

| 业务 | 主要状态 |
|---|---|
| 课题 | 草稿、进行中、暂停、关闭 |
| 指标 | 草稿、已下发 |
| 成果 | 草稿、预审中、预审退回、预审通过、已投稿、正式材料待审、初审退回、终审中、终审退回、已生效 |
| 月季报 | 草稿、初审中、初审退回、终审中、终审退回、已通过 |
| 文件 | 待上传、可用、已删除 |
| 归档文件夹 | 待判断、适用、不适用；不存在审批状态 |
