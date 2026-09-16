# 第 6 步文件公共能力：独立基础改动提案

## 原因和范围

状态（2026-09-16）：用户已明确选择“保持 B 边界，文件能力等待 A 提供”。本次不实施 A 模块改动，也不表示 A/C 已评审。成果无附件草稿可以独立使用；非空附件请求返回 503 并回滚。

当前 FileService 的文件元数据/归属校验为 private，公开接口只有上传票据、完成上传和签名 URL。B 无法安全判断已有 fileId 是否 READY、属于当前上传人，以及构建 FileObject 响应。按 AGENTS.md 不得从 B 直接访问 FileObjectMapper。

建议本次作为独立文件基础任务，仅改以下 A 文件及对应测试，不改 auth/common/config/POM 或公共 OpenAPI FileObject：

1. FileService.java：新增两个公开方法，复用原私有校验。
2. file/application/BusinessFileAccess.java：增加业务授权回调接口。
3. FileServiceTest.java：增加归属、READY、类别、关联授权正反例。

## 可审查的接口及行为

```java
public FileView requireOwnedAchievementFile(long fileId);
public FileView readMetadata(long fileId);

public interface BusinessFileAccess {
    boolean canRead(long fileId);
}
```

- requireOwnedAchievementFile：必须当前用户本人上传，READY、未删除；objectKey 必须以服务端上传票据生成的 achievement/ 开头，防止把 ARCHIVE 文件关联为成果材料。失败分别使用现有 FILE_OWNER_REQUIRED、FILE_NOT_READY、FILE_NOT_FOUND，类别错误新增 FILE_BUSINESS_TYPE_MISMATCH（422）。
- readMetadata：复用 requireReadable，返回已有 FileView，不返回存储路径或永久 URL。
- requireReadable：保留全局角色/上传者规则，额外允许 BusinessFileAccess 的任一已注册实现确认可读；仍统一拒绝非 READY 或已删除文件。
- FileService 通过 ObjectProvider<BusinessFileAccess> 延迟获取回调，避免文件服务与业务服务构造循环；没有回调时保持原权限行为。
- B 在 achievement 内实现回调，必须找到真实材料关联，按当前用户与课题范围校验。客户端不能提交“已授权”标记，未关联文件不能借此绕过权限。

## 验证要求

新增单元测试至少覆盖：本人 READY 文件允许关联、他人/未完成/删除/错误业务类别拒绝；元数据和签名下载遵守同样权限；业务回调允许当前可读材料、拒绝无关文件；无回调时原行为不变。B 集成测试再覆盖跨单位、旧牵头、停用成员及未关联文件。

这是一份供 A 后续实施和 A/C 评审的具体提案。B 已预留 AchievementFileGateway 和 AchievementService.canReadFile，但没有生产适配器，不能将其视为文件授权已经接通。A 提供能力后，由 B 添加适配器并补充真实材料关联、历史版本及跨单位下载集成测试。

还需核对 FileView.createdAt 当前 LocalDateTime 与 OpenAPI FileObject 的 RFC3339 时间要求；不得为了本步绕过公共契约评审直接修改 FileObject。
