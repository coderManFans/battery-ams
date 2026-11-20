# 本地部署

依赖 Docker Desktop / Compose v2、JDK 21、Maven 3.9+、Node.js 20+。

启动基础设施：`docker compose -f deploy/docker-compose.yml up -d`。
RocketMQ NameServer：`localhost:9876`；Broker：`localhost:10911`。本地开发网络内服务连接 NameServer 使用 `rocketmq-namesrv:9876`，宿主机运行的服务使用 `localhost:9876`。\n停止：`docker compose -f deploy/docker-compose.yml down`。
清理数据：`docker compose -f deploy/docker-compose.yml down -v`（删除本地数据卷）。

后端资产服务：`cd backend && mvn -pl asset-service spring-boot:run`（8081）。\n仓储服务：`cd backend && mvn -pl warehouse-service spring-boot:run`（8082）。
前端：`cd frontend && npm install && npm run dev`。

演示账号仅用于本地开发，严禁用于生产。