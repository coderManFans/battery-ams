# OpenAPI

asset-service 启动后提供动态 OpenAPI 3 文档：

- JSON：`http://localhost:8081/v3/api-docs`
- Swagger UI：`http://localhost:8081/swagger-ui/index.html`

将 JSON URL 配置到 AI 研发测试平台的“接口文档管理”中即可导入当前服务 API。接口契约与平台用例建议在 CI 中持续回归。
