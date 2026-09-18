# A：平台与集成任务单

## 阶段1（已建立的公共基线）

- 维护 Java 21、Spring Boot、MyBatis-Plus、Security、MySQL、Redis、Flyway 和 Nginx 基线。
- 维护 JWT 登录、刷新令牌轮换、统一错误、分页、审计及课题数据范围公共接口。
- 维护用户、角色、权限、单位和文件存储抽象。
- 审核所有数据库迁移、公共 OpenAPI 模型和跨模块依赖。

## 阶段2

- 完成认证、用户、角色权限和单位查询 API。
- 提供 `CurrentUser` 与 `TopicAccessService`，并覆盖允许、拒绝两类权限测试。
- 提供 FILESYSTEM 文件上传票据、完成上传、预览/下载签名地址接口，保留 MinIO/S3 替换点。
- 维护前端统一 HTTP Client、JWT 与 401/403 处理，但不替 B、C 编写业务页面。

## 完成标准

- 后端测试、MySQL 迁移、OpenAPI 校验通过。
- 外部课题单位永远不能获得自筹权限。
- 停用账号或修改密码后旧令牌失效；刷新令牌只能轮换使用一次。
- B、C无需访问 A 的 Mapper 即可使用公共能力。
