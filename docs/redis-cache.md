# Redis 缓存配置

本项目默认使用本地缓存（Caffeine / Spring Boot 默认），你可以通过配置在启动时切换到使用 Redis 作为缓存存储。

如何启用

1. 在应用配置中设置（二选一）：

```yaml
halo:
  cache:
    type: redis

# 或者使用 Spring 的标准配置
spring:
  cache:
    type: redis

spring:
  redis:
    host: localhost
    port: 6379
```

2. 确保 Redis 服务可达（`spring.redis.host`/`port`），并在生产环境中根据需要配置密码、SSL 等。

实现说明

- 我们在 `application` 模块中提供了 `RedisCacheConfiguration`：当 `halo.cache.type=redis` 或 `spring.cache.type=redis` 时且类路径中存在 `RedisConnectionFactory`，该配置会激活并创建 `RedisCacheManager`。
- 在未启用 Redis 的情况下，项目保持现有的缓存实现，不会出现 bean 冲突。

可扩展点

- 如果需要为不同的 cache 名称配置不同的过期时间（TTL），可以扩展 `RedisCacheConfiguration`，通过 `RedisCacheManager.builder().withInitialCacheConfigurations(...)` 提供 per-cache 配置。
- 若希望在 CI/容器中进行集成测试，建议使用 Testcontainers 启动临时 Redis 实例。
