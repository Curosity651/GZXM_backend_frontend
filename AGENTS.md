# GZXM repository collaboration rules

## Module ownership

- A owns `backend/.../auth`, `system`, `file`, `common`, `config`, infrastructure and migration coordination.
- B owns `backend/.../topic`, `indicator`, `achievement`.
- C owns `backend/.../report`, `archive`, `dashboard`.
- Frontend work follows the same business ownership under `frontend/src`.

Do not access another module's Mapper directly. Use its public application service or an agreed contract.

## Shared and frozen areas

`backend/pom.xml`, `common`, `config`, `auth`, existing migrations, OpenAPI common models, root infrastructure and frontend global HTTP/session code require a dedicated foundation task and review by the other two members.

Never edit a merged Flyway migration. Add a new migration with a unique timestamp version.

When adding a new business module, add its `repository` package to `@MapperScan`; never scan the whole modules tree because public service interfaces are not MyBatis mappers.

## Required checks

- Backend: `./mvnw test` using Java 21.
- Frontend: `npm test`, `npm run lint`, `npm run build`.
- OpenAPI changes must pass contract validation.
- Authorization changes require at least one allowed and one rejected test.
- Never commit passwords, tokens, private keys, production URLs or real user data.
