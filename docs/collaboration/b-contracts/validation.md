# B 第 1 步验证记录

日期：2026-09-15。变更仅为 B 契约评审文档及配套只读校验脚本。

## 检查结果

| 检查 | 结果 | 覆盖范围 |
|---|---|---|
| Java 21 `.\mvnw.cmd test` | 通过：13 项，失败 0、错误 0、跳过 0 | 已有认证、系统、课题范围公共服务及文件测试；不是 B 业务实现测试 |
| 前端 `npm test` | 通过：18 个测试文件、94 项测试 | 已有原型回归；Node 24.19.0 |
| 前端 `npm run lint` | 通过：退出码 0 | 原 oxlint 规则，加载同版本 Windows 原生包 |
| 前端 `npm run build` | 通过：退出码 0 | TypeScript 与 Vite 生产构建 |
| `validate.py` | 全部通过 | OpenAPI 3.1 及本地引用、67 个唯一操作、24 个 B 操作精确映射、27 张基线表、11 张 B/共享表字段、12 个请求格式正反例、13 项决策及文档链接 |
| MySQL 迁移/真实 API/E2E | 本步骤未执行 | 没有数据库或运行时变更；不能据此宣称通过 |
| A/C 与业务评审 | 尚未完成 | 需要处理 README 中 D01—D13 |

## 环境问题与处理

1. 沙箱内 Maven Wrapper 报空数组错误；允许沙箱外执行后，原始 `.\mvnw.cmd test` 成功，无需修改 Wrapper。
2. 沙箱内 Node 文件访问报 EPERM；沙箱外默认 Node 20.16.0 又因 jsdom 的 ESM 依赖启动失败，没有用例实际运行，不能视为通过。
3. 现有 Vite 要求 Node `^20.19.0 || >=22.12.0`，jsdom 要求 `^20.19.0 || ^22.13.0 || >=24.0.0`。复验使用已安装 Node 24.19.0，仅修改该测试进程 PATH；未改变系统 Node、package.json、锁文件或其他基础配置。
4. OpenAPI 检查器安装在临时虚拟环境，不增加后端/前端业务依赖。
5. 已有 oxlint 1.75.0 缺少 `@oxlint/binding-win32-x64-msvc`。仅将同版本原生包安装到临时目录，通过官方加载器已有的 `NAPI_RS_NATIVE_LIBRARY_PATH` 入口加载，再运行原 lint 命令通过；没有删锁文件、修改规则或重装整套项目依赖。
6. 测试输出有 jsdom 伪元素 getComputedStyle 未实现提示；构建有现有大于 500 kB 分块提示。两者未导致失败，本步骤不扩大范围修改 UI 或打包配置。
7. 以上为工作区已有依赖下的基线复验，不是空环境 `npm ci` 可重复安装验证。实际使用 oxlint 1.75.0、Vite 8.1.5；未升级它们。

## 本机复验命令

从仓库根目录执行契约检查：

```powershell
& "$env:TEMP\gzxm-b-contract-check\Scripts\python.exe" docs/collaboration/b-contracts/validate.py
```

前端复验（已安装的 Node 24 路径只适用于本机；其他开发机使用满足要求的 Node）：

```powershell
$env:Path = 'C:\Users\97869\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin;' + $env:Path
node --version
Set-Location frontend
node 'C:\Program Files\nodejs\node_modules\npm\bin\npm-cli.js' test
# 仅在本机仍缺原生包时执行安装和环境变量两行；已有匹配原生包的环境直接运行 lint。
node 'C:\Program Files\nodejs\node_modules\npm\bin\npm-cli.js' install --prefix "$env:TEMP\gzxm-b-lint-runtime" --no-save --package-lock=false --ignore-scripts @oxlint/binding-win32-x64-msvc@1.75.0
$env:NAPI_RS_NATIVE_LIBRARY_PATH = "$env:TEMP\gzxm-b-lint-runtime\node_modules\@oxlint\binding-win32-x64-msvc\oxlint.win32-x64-msvc.node"
node 'C:\Program Files\nodejs\node_modules\npm\bin\npm-cli.js' run lint
node 'C:\Program Files\nodejs\node_modules\npm\bin\npm-cli.js' run build
```

以上修改只影响当前 PowerShell 进程。如果希望恢复默认 PATH，关闭该窗口即可。

每条命令执行后检查 `$LASTEXITCODE` 为 0；不要因为最后一条命令成功而忽略之前的失败。

## 分支与变更检查

- 当前为“分支B”，跟踪 `origin/分支B`。
- 核对时本地和远端 main 均保持 `5217f6ac4c0bb69e9053585cd7fc0d3b7ec7a8da`。
- 本步骤未提交、推送或创建 PR。新增文件位于 `docs/collaboration/b-contracts/`，在 B 任务单增加入口。
- 未修改后端源码、前端源码、POM、全局配置、OpenAPI 原契约、建表脚本或既有迁移。
- `.workbuddy/`、`backend/backend-run.log` 是原有未跟踪文件，保持原状。

## 本地部署的可见效果

本步骤不新增接口和页面，无需重新部署、重启或执行 SQL。可直接阅读契约包、运行静态检查并评审决策清单。现有页面行为保持原样；Mock 演示或 Swagger 中已有端点不能作为 B 新功能验收证据。

第 1 步工程梳理完成与契约获准冻结是不同状态；缺少审批记录时不会将签署状态改为通过。
