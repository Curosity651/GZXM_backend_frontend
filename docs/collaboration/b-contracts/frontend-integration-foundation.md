# 第九步前端联调所需的独立基础改动

## 已核实的缺口

- `api/auth-api.ts` 已提供真实登录、当前身份与退出；`store/index.ts` 的 login/logout 仍仅操作 Mock 用户，`AuthGuard` 仍读取 Mock 用户和角色。
- `api/http-client.ts` 已负责 Token、刷新与错误处理，但仅返回 JSON。指标草稿的 `X-Draft-Version`（包括空草稿）及分配的 `X-Topic-Indicator-Version` 无法由调用方读取，不能从空数组推断版本为零。

## 决策

2026-09-17 用户明确选择“保持 B 边界，基础能力等待 A 提供”。本次不实施下述共享源码修改，不标记 A/C 已评审。B 的独立页面测试使用 HTTP 测试替身，并非共享登录闭环已通过。

## 拟议范围（独立基础任务，由 A 提供并完成共享变更互审）

1. 公共 HTTP 增加保留响应头的请求入口，原 apiRequest 返回值保持兼容；复用原认证、单次刷新、错误处理，不另写一套 fetch。
2. 全局 Store、AuthGuard、布局和登录页按现有 VITE_API_MODE 分支接入 authApi；真实模式只信任服务端身份、页面/动作权限及成员关系；不将真实用户密码或 Token 写入持久化业务 Store。刷新页面恢复真实会话，退出及会话失效清除真实身份。
3. 真模式移除演示账号提示；其他 A/C 业务页面未联调前明确其状态，不让 Mock 菜单权限授予真实业务操作。
4. 测试覆盖五角色、刷新恢复、401/403、退出与会话失效、并发刷新、空草稿版本头及原 Mock 模式回归。

## B 已准备的对接位置

- B 页面调用现有 `authApi.me()`，只使用真实 `roleCode/pagePermissions/actionPermissions`；不将 Mock 用户视为真实登录身份。页面最外层 `AuthGuard` 和登录跳转仍需 A 接通。
- `frontend/src/api/research/client.ts` 导出 `MetadataRequest` 与 `configureResearchMetadataRequest`。由 A 在应用启动时注册统一的请求适配器，返回 `{ data, headers: Headers }`，并沿用公共 Token 刷新、错误处理与 credentials。不要另写独立 fetch。
- 适配器必须保留 `X-Draft-Version` 和 `X-Topic-Indicator-Version`。跨域部署时由 A 检查 CORS expose headers；同源反向代理也不得丢弃这两个头。
- 未注册适配器时只禁用指标草稿编辑/发布；已下发指标查询正常调用公共 apiRequest。缺失/非法版本头明确失败，包括空草稿，禁止从行的发布版本推断草稿版本。
- 文件能力仍按 `achievement-file-foundation.md` 由 A 提供，属于另一个既有依赖。

这不包括后端 A 文件服务，不改变此前等待 A 提供成果文件能力的决定。B 的业务页面、类型、API 调用及测试继续独立实施。未授权前不修改上述冻结源码，未获得互审前不标记 A/C 已批准；完整浏览器登录闭环验收仍待基础改动交付。
