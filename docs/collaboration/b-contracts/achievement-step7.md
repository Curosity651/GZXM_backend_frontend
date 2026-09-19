# 第 7 步：成果流转、两级审批与不可变快照

日期：2026-09-16；分支：`分支B`。

**B 的流程代码和自动化测试已完成；真实文件依赖未解除。** 当前可在部署后端跑通无附件预审、两级审批、退回重提、投稿/申请登记及进入正式草稿。正式和补充提交在 A 文件能力接入前返回 503，不允许绕过材料校验。完整带材料闭环尚未实机验收。

## 1. 用户确认的业务规则

- 五类都从 DRAFT 开始，均需科研助理初审、项目技术负责人终审；人才不跳过预审。
- 论文、专利预审通过后登记投稿/申请，再进入正式草稿。其他三类预审终审通过直接进入正式草稿。
- 五类成果在正式终审通过后计入完成；论文须已录用、专利须已授权、软件著作权须取得证书。论文、专利仍保留后续补充材料两级审批，进入或退回补充阶段不撤销已有完成计数。
- 所属单位仅能编辑草稿、退回和待补充状态；审批中及生效后锁定。任何退回重提都从初审重新开始。
- 动作必须携带 recordVersion；审批同时携带 recordVersion、submittedVersion，退回意见必填。
- 同一幂等键、同一请求返回原结果；同键不同请求 409。
- 预审不强制附件，以标题、负责人及已填详情合法为最低要求。正式、补充必须满足已确认日期及当前前端材料清单；文件能力未接入时拒绝提交。

## 2. 状态与动作

| 状态 | 单位动作 / 审批 | 下一状态 |
|---|---|---|
| DRAFT、PRE_RETURNED | SUBMIT_PRE_REVIEW | PRE_INITIAL |
| PRE_INITIAL | 初审通过 / 退回 | PRE_FINAL / PRE_RETURNED |
| PRE_FINAL | 终审通过 | 论文、专利 → PRE_APPROVED；其他三类 → FORMAL_DRAFT |
| PRE_FINAL | 终审退回 | PRE_RETURNED |
| PRE_APPROVED | REGISTER_EXTERNAL_SUBMISSION | EXTERNAL_SUBMITTED，仅论文、专利 |
| EXTERNAL_SUBMITTED | START_FORMAL | FORMAL_DRAFT |
| FORMAL_DRAFT、FORMAL_RETURNED | SUBMIT_FORMAL | FORMAL_INITIAL |
| FORMAL_INITIAL | 初审通过 / 退回 | FORMAL_FINAL / FORMAL_RETURNED |
| FORMAL_FINAL | 终审通过 | 论文 → WAIT_PUBLICATION；专利 → WAIT_GRANT；其他三类 → EFFECTIVE |
| FORMAL_FINAL | 终审退回 | FORMAL_RETURNED |
| WAIT_PUBLICATION、WAIT_GRANT、SUPPLEMENT_RETURNED | SUBMIT_SUPPLEMENT | SUPPLEMENT_INITIAL，仅论文、专利 |
| SUPPLEMENT_INITIAL | 初审通过 / 退回 | SUPPLEMENT_FINAL / SUPPLEMENT_RETURNED |
| SUPPLEMENT_FINAL | 终审通过 / 退回 | EFFECTIVE / SUPPLEMENT_RETURNED |
| EFFECTIVE | 编辑、再次提交、再次审批 | 拒绝 |

正式终审通过后 countsToIndicator=true；论文、专利后续处于待补充、补充审批或补充退回状态时仍保持 true，补充终审后状态转为 EFFECTIVE。阶段转换更新同一条成果，不复制新成果，不对目标数量执行加一。第 8 步消费该完成标记实现累计和专项统计。

允许编辑状态精确为 DRAFT、PRE_RETURNED、FORMAL_DRAFT、FORMAL_RETURNED、WAIT_PUBLICATION、WAIT_GRANT、SUPPLEMENT_RETURNED。PRE_APPROVED 和 EXTERNAL_SUBMITTED 仅用于阶段动作，不允许编辑。

## 3. 接口与权限

接口前缀 `/api/v1`：

| 接口 | 权限及行为 |
|---|---|
| POST /achievements/{id}/actions | achievement.submit + 内部/外部单位身份 + 当前所属单位和有效成员；校验当前下发指标及课题状态 |
| POST /achievements/{id}/reviews | 科研助理 + achievement.initial.approve，或技术负责人 + achievement.final.approve；不得越级，系统管理员即使误配权限也不能代审 |
| GET /achievements/{id}/snapshots | 与成果详情相同的当前数据范围；按提交版本倒序 |
| GET /achievements/{id} | 新增 approvals，单独展示审批历史，不把快照当审批记录 |
| GET /achievements?pendingForMe=true | 科研助理初审队列、技术负责人终审队列；需对应动作权限，其他用户为空 |

动作和审批头部必须有 8..100 个可见 ASCII 字符的 `Idempotency-Key`。大小写有别。同一个用户的成果动作与审批共用键空间，键不能跨成果或跨操作复用。请求内容按解析后的 JSON 比较；JSON 字段顺序不同不构成不同业务请求。

每次成功编辑、动作、审批递增 recordVersion。每次 SUBMIT_PRE_REVIEW / SUBMIT_FORMAL / SUBMIT_SUPPLEMENT 递增 submittedVersion、更新 submitted_at 并插入快照；登记和 START_FORMAL 不产生提交快照。审批保存自己实际处理的 stage、level、提交版本和操作者。

有效重试返回当时的响应，即使成果之后已经进入下一阶段，也不把旧请求重新执行、不重复快照或审计。重试仍校验调用者当前身份、权限及数据范围；已失去权限的用户不能借旧键访问历史。课题只读后新操作被拒绝，有权限的旧成功请求重放不写数据。

登记投递日期必填且须有效 YYYY-MM-DD，编号可选，最多 500 字符。论文映射 detail.submissionDate / externalSubmissionNumber；专利映射 detail.applicationDate / applicationNumber。其他动作不得携带这些登记字段。PUT 的 detail 仍完整替换，前端保存时须带上要保留的字段。

## 4. 阶段材料与日期

| 类型 | 正式提交日期和状态 | 正式必需材料 |
|---|---|---|
| PAPER | acceptanceDate；paperStatus 已录用或已正式刊出 | 论文定稿、录用通知或接收函、项目标注页 |
| PATENT | grantDate；patentStatus 已授权 | 专利授权证书、专利授权文件、项目关联说明 |
| COPYRIGHT | certificateDate | 软件著作权证书、软件鉴别材料、著作权人证明 |
| STANDARD | draftCommitDate | 标准送审稿、送审或立项证明 |
| TALENT | actualGraduationDate | 研究生学位论文证明材料 |

论文补充：publicationDate、paperStatus=已正式刊出；正式刊出论文全文、期刊封面、目录及见刊页（一个材料类别）、项目标注页；SCI/EI/CSCD 另需检索证明，isChineseCoreJournal=true 另需中文核心期刊认定证明。

专利补充：grantDate、patentStatus=已授权；专利授权证书、授权公告文本、法律状态证明、专利权属证明。

读取当前 active 材料，要求文件 READY，不允许拿退役的旧材料凑必需类别。提交把当前材料标记 SUBMITTED，退回标记 RETURNED，终审通过标记 APPROVED；此前快照不随这些变化修改。审批通过时再次核对阶段必填和材料，退回不要求材料齐全。

当前没有 AchievementFileGateway 生产适配器，正式/补充提交优先返回 503 / FILE_REFERENCE_CAPABILITY_UNAVAILABLE，数据库状态、版本、快照、材料状态和重试记录均不变化。第 6 步已说明的现有附件详情限制仍存在。A 提供公开接口并完成授权联调后，才可验证真实带文件闭环。

## 5. 存储、事务与边界

- 新增 `V202609160930__add_achievement_workflow_operations.sql`，仅新增 B 的 achievement_workflow_operation，存成功请求和原响应；不修改已存在迁移、A 的 api_idempotency 或通用服务。
- 使用既有共享 approval_record / submission_snapshot，B Mapper 的每个读写固定 business_type='ACHIEVEMENT'，没有改共享表结构，没有调用 C 的 Mapper。带同 business_id 的 REPORT 夹具测试证明不会混入成果结果。
- 先通过公开 TopicQueryService 锁课题，再锁成果，协调成员/状态/编辑与审批。状态、材料状态、快照、审批、成功重试记录在同一事务提交；任一步失败全部回滚。
- 快照固化提交时业务字段、文件元数据及当前材料关联，去掉独立的审批历史。重提创建新版本，不能覆盖旧快照。
- 成功审计沿用提交后的公共 AuditService；其独立事务故障恢复仍由 A 统一处理，不因本步自动解决。
- 新增 B 专用 AchievementReviewRequest 约束，不修改报告使用的共享 ReviewRequest，以及共享 ApprovalRecord、SubmissionSnapshot、Problem、FileObject。
- 合并前 A/C 对迁移、共享表协作和契约的人工评审仍未代签。

## 6. 测试结果与范围

- Java 21 全量 Maven **131 项通过，0 失败、0 错误、0 跳过**；较第 6 步增加 33 项：22 项流程集成测试、10 项流转矩阵测试、1 项生产环境缺少文件能力的拒绝测试。
- MySQL 8.4.3 + Spring Boot/MockMvc：五类预审和正式流程、论文/专利补充认定、允许/拒绝权限、不可变旧快照、正式/补充退回重提、并发同键重放、相反审批竞争、请求冲突、过期版本、材料/日期缺失、失败回滚、待办及 REPORT 隔离。
- 正式/补充成功测试使用 B 文件边界的测试替身和合成文件，不冒充 A 的真实归属校验、上传、下载或生产适配器验收。
- 空库五条 Flyway 迁移成功，重复启动不重复迁移。未修改部署数据库。
- OpenAPI 3.1 静态检查、15 个请求格式正反例、本步 3 个真实无附件流程响应与此前 20 个响应校验通过。
- 前端 **94 项通过**，lint、build 通过；原有伪元素环境提示及大包提示仍在。
- synthetic snapshot failure、synthetic operation failure 和既有 synthetic failure 日志属于故障注入测试的预期现象。

复验后端：Java 21，在 backend 执行 `.\mvnw.cmd test -B -ntp`；默认 Docker/Testcontainers，无 Docker 时使用 GZXM_TOPIC_TEST_MYSQL_URL 指向本机 gzxm_topic_test_* 隔离库，不能使用部署库。随后运行 `python docs/collaboration/b-contracts/validate.py` 和 `validate-achievement-workflow-responses.py`；Python 需已安装 openapi-spec-validator==0.7.2。

## 7. 本地部署验证

1. 在分支B使用原配置重启后端，例如 `scripts/run-backend.ps1`，Flyway 自动应用新增迁移。不要重复手工运行基线建表脚本；应用与数据库时区保持一致。
2. 按第 6 步创建无附件草稿，以下假设返回 id=123、recordVersion=1，须替换实际 ID。准备所属单位、科研助理、项目技术负责人三个本地账号及相应权限。请求都携带登录令牌与 Content-Type: application/json。
3. 单位请求 POST `/api/v1/achievements/123/actions`，头部 `Idempotency-Key: local-pre-submit-001`：

```json
{"action":"SUBMIT_PRE_REVIEW","recordVersion":1}
```

预期 200、PRE_INITIAL、recordVersion=2、submittedVersion=1、不计完成量。重复同请求返回相同结果，只生成一份快照。

4. 科研助理查询 GET `/api/v1/achievements?pendingForMe=true` 应出现该项。POST `/api/v1/achievements/123/reviews`，新键 `local-pre-initial-001`：

```json
{"decision":"APPROVE","recordVersion":2,"submittedVersion":1,"opinion":"本地验证初审通过"}
```

预期 201、level=INITIAL；再次 GET 成果为 PRE_FINAL、recordVersion=3。技术负责人用新键提交同路径、decision=APPROVE、recordVersion=3、submittedVersion=1，预期 201。成果版本变为 4；论文/专利为 PRE_APPROVED，其他三类为 FORMAL_DRAFT，全部仍不计数。

5. GET `/api/v1/achievements/123/snapshots` 查看版本 1 的提交内容；GET 成果详情的 approvals 可见两条记录。换无关单位读取应 403。在另一条草稿上用 RETURN 和非空 opinion 验证退回，修改后携带最新版本重提，submittedVersion 应增加且旧快照不变。
6. 若是论文/专利，由单位使用新键调用 actions：

```json
{"action":"REGISTER_EXTERNAL_SUBMISSION","recordVersion":4,"externalSubmissionDate":"2026-09-16","externalSubmissionNumber":"LOCAL-001"}
```

预期 EXTERNAL_SUBMITTED、版本 5；再用新键提交 `{"action":"START_FORMAL","recordVersion":5}`，预期 FORMAL_DRAFT、版本 6。日期应与自己已有详情一致。

7. 在正式草稿状态使用最新版本提交 SUBMIT_FORMAL：当前预期 503 / FILE_REFERENCE_CAPABILITY_UNAVAILABLE；再 GET 确认状态和版本没变。这验证依赖保护，不能作为正式认定成功的证据。

**前端仍未接入真实成果 API，本步没有新增可点击验收的界面。** 使用 Postman 或其他 HTTP 客户端验证上述流程。前端联调属于第 9 步；文件真实联调继续等待 A。
