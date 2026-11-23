# Battery AMS 全栈演示启动

> 本文件提供开发演示环境，不是生产部署方案。默认数据库口令只用于本地。

## 前置条件

Docker Desktop / Docker Compose v2。建议给 Docker 至少 8 GB 内存，首次 Maven 构建会下载依赖，可能耗时较长。

## 一键启动

在仓库根目录执行：

```bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.full.yml up --build -d
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.full.yml ps
```

前端地址：http://localhost:5173

后端端口：资产 8081、仓储 8082、工单 8083、保全 8084、遥测 8085。

## 检查服务

```bash
for port in 8081 8082 8083 8084 8085; do
  curl -fsS "http://localhost:$port/actuator/health" || echo "service $port not ready"
done
```

如果某个服务不可用，查看日志：

```bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.full.yml logs --tail=100 asset-service warehouse-service workorder-service preservation-service telemetry-service
```

## 停止

```bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.full.yml down
```

不要随意使用 `down -v`：它会删除 MySQL、Redis、RocketMQ 数据卷。

## 注意

- 此版本使用 Vite 开发服务器提供前端，仅用于本地演示。
- 后端服务使用容器内部 DNS 连接 MySQL、Redis 和 RocketMQ。
- 此阶段只完成容器化配置提交；需要在具备 Docker 的环境执行全栈启动与跨服务验收后，才能确认端到端通过。
- 如果本地已经运行相同端口的服务，请先停止，避免端口冲突。
