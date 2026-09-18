# GZXM 重点项目科研管理系统前端

本目录是与 Spring Boot 后端联调的 React 前端。运行时不再提供 Mock/真实 API 切换，登录、权限、课题、指标、成果、月季报、归档和文件操作统一调用 `/api/v1` 后端接口。

## 技术栈

- React 19、TypeScript、Vite
- Ant Design
- Zustand（仅保存当前真实登录会话）
- React Router

## 本地运行

先从仓库根目录启动 MySQL、Redis 和后端，再启动前端：

```powershell
cd C:\Users\86178\Desktop\GZXM\gzxm_qianhouduan
.\scripts\start-local.ps1
.\scripts\run-backend.ps1
.\scripts\run-frontend.ps1
```

前端默认地址为 `http://127.0.0.1:5173`，Vite 将 `/api` 代理到 `http://127.0.0.1:8080`。

## API配置

开发和生产环境均使用：

```env
VITE_API_BASE_URL=/api/v1
```

系统不再识别 `VITE_API_MODE`，也不会因环境变量缺失退回浏览器 Mock 数据。

## 构建

```powershell
cd frontend
npm install
npm run build
```

后端接口契约见 `../docs/api/openapi.yaml` 和 `../docs/api/api-reference.md`。
