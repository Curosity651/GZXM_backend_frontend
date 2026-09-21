# NAS WebDAV 文件存储

系统中的实际业务上传入口（成果材料、国家材料、配套自筹材料）统一经过 `FileService`。将 `FILE_PROVIDER` 设为 `WEBDAV` 后，新文件会写入 NAS；数据库只保存文件元数据和 NAS 对象键，不保存文件正文。

## 必需配置

```dotenv
FILE_PROVIDER=WEBDAV
FILE_SIGNING_SECRET=请使用独立的至少32位随机字符串
NAS_WEBDAV_BASE_URL=http://127.0.0.1:18080/webdav/gzxm/
NAS_WEBDAV_USERNAME=NAS服务账号
NAS_WEBDAV_PASSWORD=NAS服务密码
NAS_WEBDAV_CONNECT_TIMEOUT=5s
NAS_WEBDAV_REQUEST_TIMEOUT=120s
```

- `NAS_WEBDAV_BASE_URL` 必须指向一个允许 `MKCOL`、`PUT`、`MOVE`、`GET`、`HEAD/PROPFIND`、`DELETE` 的专用目录。
- 建议使用权限受限的独立 NAS 服务账号，不要使用管理员账号。
- 系统不会在代码、数据库或错误日志中保存 NAS 密码。
- 历史 `FILESYSTEM`/`MOCK` 文件仍可通过本地存储适配器读取；切换后创建的新票据使用 `WEBDAV`。

## 上传一致性

上传先落到后端临时文件，验证声明大小后写入 NAS 临时对象，再用 WebDAV `MOVE` 原子替换正式对象。完成接口会再次校验 NAS 对象是否存在及大小是否一致。相同文件票据的上传和完成操作通过数据库行锁串行化；未完成超过 24 小时的对象每天清理一次。

## 上线检查

1. 在部署环境填写上述变量并重启后端。
2. 使用普通业务账号上传一个小型 PDF。
3. 确认 `file_object.storage_provider=WEBDAV` 且状态为 `READY`。
4. 在 NAS 专用目录确认对象存在，再从成果或归档页面预览/下载并核对内容。
5. 临时断开 NAS，确认上传返回 `FILE_STORAGE_UNAVAILABLE`，并能在“系统管理 → 系统错误日志”中按追踪号检索。
