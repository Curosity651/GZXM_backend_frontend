# C 模块本地验证记录

本机验证工作树：`team-c-work`。MySQL 与 Redis 由 `infrastructure/compose.yaml` 启动；本地密码只在被 Git 忽略的根目录 `.env` 中。

1. 确认 `/opt/local/bin/colima status` 与 `/opt/local/bin/docker ps` 正常，并在工作树根目录运行 `/opt/local/bin/docker compose --env-file .env -f infrastructure/compose.yaml up -d`。
2. 在 `backend` 目录运行 `set -a; source ../.env; set +a; bash ./mvnw spring-boot:run`。`curl http://localhost:8080/actuator/health` 应返回健康状态。首启由 Flyway 自动创建表。
3. 管理员本地用户名是 `admin`，随机密码在根目录 `.env` 的 `APP_BOOTSTRAP_ADMIN_PASSWORD`。本地联调用户的密码也在 `.env`，前缀为 `C_SMOKE_`。这些凭据不应上传。
4. 文件内容写在根目录 `.env` 的 `FILE_STORAGE_ROOT` 指定目录（默认 `backend/storage/files`），文件上传和下载请通过 `/api/v1/files`，不要直接访问此目录。
5. 前端 `npm test`、`npm run lint`、`npm run build` 可独立运行。真实 API 模式的 C 页面已接接口，但 A 的真实登录和会话尚未接入前端路由；目前真实业务链路可通过后端 API 验证，前端默认仍为演示模式。

测试容器若使用 Docker Engine 29 和仓库当前 Testcontainers 版本，可用以下仅影响当前命令的环境设置运行后端测试：

```sh
cd backend
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_RYUK_DISABLED=true \
JAVA_TOOL_OPTIONS=-Dapi.version=1.44 \
bash ./mvnw test
```

若电脑睡眠后 Colima 的临时容器端口无法连通，可以在本机 MySQL 中使用**专用测试库**运行同一套测试。测试会清理夹具，库名必须以 `gzxm_topic_test_` 开头，绝不能指向业务库 `gzxm`：

```sh
cd "/Users/taizihai/Documents/ChatGPT/GZ管理系统UI第一版/team-c-work"
test_root_password=$(awk -F= '$1=="MYSQL_ROOT_PASSWORD" {print $2}' .env)
/opt/local/bin/docker exec -e MYSQL_PWD="$test_root_password" gzxm-mysql mysql -uroot \
  -e 'CREATE DATABASE IF NOT EXISTS gzxm_topic_test_c_full CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; SET GLOBAL log_bin_trust_function_creators=1;'
cd backend
GZXM_TOPIC_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:13306/gzxm_topic_test_c_full?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
GZXM_TOPIC_TEST_MYSQL_USER=root GZXM_TOPIC_TEST_MYSQL_PASSWORD="$test_root_password" \
bash ./mvnw test
```

本机验证结果：隔离测试库下后端 143 项测试全部通过；实际服务以 `FILE_PROVIDER=FILESYSTEM` 运行后，上传、归档关联、下载及旧 `MOCK` 记录读取均通过。前端 96 项测试、lint、构建均已通过；文件预览测试确认 HTML/SVG 不作为可内联预览类型，真实 HTTP 联调确认 HTML 即使请求预览也返回 `Content-Disposition: attachment` 和 `X-Content-Type-Options: nosniff`。真实 API 联调另外覆盖了示范工程进展 299 字拒绝提交与“无”可提交、科研助理创建“有则必存”文件夹、其他人删除该文件夹被拒、自筹项目日期和状态更新，以及工作台查询。`http://127.0.0.1:8080/actuator/health` 返回 `UP`，前端 `http://127.0.0.1:5173/login` 返回 200。OpenAPI 3.1 校验通过（69 个操作）；B 的旧契约脚本总数断言仍固定为 67，需随契约评审更新。

本机因 Docker Hub 下载受限，MySQL/Redis 镜像先从可用镜像源拉取并标记为仓库要求的名称。测试用 MySQL 镜像还启用了仅供本机测试的 `log_bin_trust_function_creators=1`，用于 B 模块触发器回滚测试；这不改变仓库的部署配置。
