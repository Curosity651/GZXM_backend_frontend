# 重点项目科研管理系统

前后端一体化开发仓库。前端沿用已确认的React原型，后端采用Java 21、Spring Boot 3.5、Spring Security、MyBatis-Plus、MySQL、Redis和Flyway。

## 目录

| 目录 | 用途 |
|---|---|
| `frontend/` | React、TypeScript、Vite前端 |
| `backend/` | Spring Boot模块化单体后端 |
| `docs/` | API、数据库、架构和协作文档 |
| `infrastructure/` | MySQL、Redis、Nginx本地环境 |
| `scripts/` | Windows本地环境检查和启动脚本 |

## 本地启动

1. 将`.env.example`复制为`.env`并修改本地密码。
2. 执行`scripts\start-local.ps1`启动MySQL与Redis。
3. 使用Java 21执行`scripts\run-backend.ps1`启动后端。
4. 执行`cd frontend`、`npm install`、`npm run dev`。

默认地址：前端`http://localhost:5173`，后端`http://localhost:8080`，Swagger UI为`http://localhost:8080/swagger-ui.html`。

详细计划参见[完整后端开发计划](docs/development-plan.md)。
三人领取任务和评审方式参见[协作入口](docs/collaboration/README.md)。
