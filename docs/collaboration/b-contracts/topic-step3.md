# 第 3 步：公开课题查询契约

## 已提供的接口

后续补充：第 8 步增加 `listReadableTopics(projectId)`，并提供指标目标及成果统计契约，见[第 8 步说明](achievement-step8.md)。

接口位于 `com.gzxm.server.modules.topic.application`，可直接按接口类型构造器注入。实现是 Spring Bean，返回不可变 record 和不可修改的列表，不暴露数据库实体或 Mapper。

| 接口方法 | 输入 | 输出及规则 |
|---|---|---|
| TopicQueryService.getTopic | 正整数 topicId | TopicSummary：id/projectId/code/name/leadUnitId/status/enabled；检查当前用户读取范围 |
| TopicQueryService.listMembers | topicId、includeDisabled | Member：membershipId/topicId/unitId/membershipType/enabled；false 仅有效关系，true 包含历史关系，但仍先校验访问范围 |
| TopicQueryService.isLeadUnit | topicId、unitId | 先检查课题读取范围，再判断指定单位是否为课题指向的有效 LEAD |
| TopicIdentityFacts.activeMembershipsForUnit | 正整数 unitId | 可信进程内身份加载使用；不依赖 CurrentUser；只返回有效关系，按 topicId、关系 id 稳定排序；无关系返回空列表 |

Java 契约 ID 使用 long；HTTP 契约继续使用字符串 ID。本次没有新增 HTTP 路由或修改 OpenAPI。

## 范围和状态约定

- 系统管理员、项目技术负责人、科研助理可读全局；单位只可读当前仍有有效成员关系的课题。
- includeDisabled=true 不能让已被停用的调用单位重新获得读取资格。已获准读课题的调用者可查看其历史成员，与第 2 步 HTTP 成员查询规则一致。
- 暂停、关闭、停用课题仍可按上述范围读取。isLeadUnit 在此时仍可返回 true，因为它表示身份事实，不表示允许创建、提交或审批。
- 业务调用方必须自行检查动作权限、课题可操作状态和数据归属。不得用 isLeadUnit 替代这些检查；跨事务读取的事实也不是后续写入的锁或授权凭据。
- 可信身份接口不按课题 enabled/status 过滤，以保留历史可读的成员身份；它不是登录接口，不负责账号、单位、角色有效性校验，也不授予任何业务动作权限。
- 不得把 TopicIdentityFacts 暴露为 HTTP 接口，或用于绕过请求数据范围。它仅供认证加载等可信内部流程使用。此次没有改动 A 的认证代码；A 后续接入仍需按共享区域规则评审。
- 当前用户缺失时，Java 服务沿用公共异常 AUTHENTICATION_REQUIRED（403）；这与 HTTP 安全过滤器的未登录 401 属于不同调用层。合法管理用户查询不存在课题返回 TOPIC_NOT_FOUND（404）；无范围用户可能先得到 403。非法 ID 返回 422。
- 实现无身份缓存，每次调用读取数据库；同一次公开查询在只读事务中完成。

## C 的调用示例

在 C 的应用服务中注入接口即可，不访问 B 的 Mapper：

```java
private final TopicQueryService topics;

public ReportApplicationService(TopicQueryService topics) {
    this.topics = topics;
}

// 在已有认证请求中读取；角色、报告动作、课题状态的写入检查由 C 负责。
var topic = topics.getTopic(topicId);
var members = topics.listMembers(topicId, false);
boolean lead = topics.isLeadUnit(topicId, currentUnitId);
```

消费者单元测试可直接 mock TopicQueryService，返回 TopicSummary/Member；无需启动数据库或依赖 DefaultTopicQueryService。本仓库 TopicIntegrationTest 按接口注入真实 Bean，作为提供方契约测试，覆盖实际 SQL、权限、停用历史和无认证身份加载。尚未声称 C 的实际报告/归档模块已完成联调或评审。

## 验证及本地效果

2026-09-15 验证结果：Java 21 全量 Maven 测试 52 项通过（topic 集成测试 39 项），0 失败、0 错误、0 跳过；真实本机隔离 MySQL 8.4.3。前端 94 项测试、lint、build 均通过；既有伪元素测试环境提示和大于 500 kB 的构建产物提示仍存在。

新增 4 项 MySQL 集成测试：

1. 接口注入、基本字段、成员列表、牵头判断、不可变返回值和非法 ID。
2. 跨课题、includeDisabled 越权、旧上下文下成员停用及匿名读取拒绝。
3. 管理角色读取关闭/停用历史、有效与全量成员区分、牵头事实与写权限分离、不存在课题。
4. 无 CurrentUser 时加载有效身份，停用关系立即消失、暂停课题保留身份、未知单位空列表和非法 ID。

复验方式沿用 [第 2 步测试环境说明](topic-step2.md)：Java 21、Docker/Testcontainers 或隔离本机 MySQL，在 backend 执行 `.\mvnw.cmd test -B -ntp`。测试会清理专用测试库夹具，不得使用部署库。

这一步是后端进程内模块接口，没有新页面或 HTTP 入口，不能通过部署页面直接看到新增效果。第 2 步的 HTTP 流程继续可用；新增能力的直接证据是契约集成测试，以及 C 后续通过接口注入成功读取课题与成员。无需新迁移、重新初始化部署库或改变前端配置。

## 边界

实现和测试均在 B 的 topic 模块；未修改 A/C 业务代码、公共安全模型、POM、Mapper 总扫描配置或既有迁移。指标目标和成果进度查询属于第 8 步，不在本步提前实现。本接口为 B 已实现的交付契约，A/C 接入评审仍需真实记录。
