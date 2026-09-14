# 三人协作入口

本目录把总计划转成三个人可以直接领取的工作边界。开始业务编码前，三人共同确认 `docs/api/openapi.yaml`、首版 Flyway 迁移和 `docs/reviews/baseline-review.md`；确认并不要求所有业务表都有实现代码。

| 人员 | 负责范围 | 开发入口 |
|---|---|---|
| A | 认证、系统管理、公共安全、文件、基础设施与集成 | [member-a.md](member-a.md) |
| B | 课题、指标、成果 | [member-b.md](member-b.md) |
| C | 月季报、归档、工作台 | [member-c.md](member-c.md) |

## 固定协作方式

1. 每项工作从 `main` 建独立分支，命名为 `feature/<模块>-<简述>`。
2. 先在 OpenAPI 中确认接口，再实现 Controller、Service 和 Mapper；不在聊天或代码中另立一份契约。
3. B、C只调用 A 提供的 `CurrentUser`、`TopicAccessService`、统一异常和文件服务，不读取 A 模块的 Mapper。
4. C需要课题关系时调用 B 的公开查询服务，不读取课题 Mapper。
5. 一个合并请求尽量只包含一个模块；公共文件变更必须请另外两人评审。
6. 迁移文件一旦合并不可修改，新变化增加新的时间戳迁移。

任务描述使用 [task-template.md](task-template.md)，提交评审前按 [review-checklist.md](review-checklist.md) 自查。
