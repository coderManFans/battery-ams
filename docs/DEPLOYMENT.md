# 本地部署

依赖 Docker Desktop / Compose v2、JDK 21、Maven 3.9+、Node.js 20+。

启动基础设施：`docker compose -f deploy/docker-compose.yml up -d`。
停止：`docker compose -f deploy/docker-compose.yml down`。
清理数据：`docker compose -f deploy/docker-compose.yml down -v`（删除本地数据卷）。

后端：`cd backend && mvn -pl asset-service spring-boot:run`。
前端：`cd frontend && npm install && npm run dev`。

演示账号仅用于本地开发，严禁用于生产。