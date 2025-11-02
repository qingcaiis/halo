这组 Kubernetes 模板为在 k8s 集群中以 StatefulSet 方式部署 Halo、MySQL 和 Redis 提供示例。

说明：

- 命名空间：模板在 `halo` 命名空间下创建资源。你可以修改 YAML 中的 `namespace` 字段或使用 `kubectl -n <ns>` 覆盖。
- MySQL：使用 `mysql:8.1.0` 镜像，单副本（replicas: 1），并创建 PVC（10Gi）。请在生产环境中替换为具有备份/高可用策略的数据库（如云上 RDS、Cluster 或 MySQL Operator）。
- Redis：使用 `redis:7.2-alpine`，单副本（replicas: 1），并创建 PVC（5Gi）。生产建议使用 Redis 集群或托管服务。
- Halo 应用：示例以 `ghcr.io/halo-dev/halo:latest` 为镜像（请替换为你构建并推送的镜像），设置了 3 个副本（StatefulSet）。

如何使用：

1. 修改镜像和敏感配置（数据库密码、secret）为你的实际值。建议将密码与证书放入 `Secret`，并通过 `envFrom`/`valueFrom` 引用。
2. 将文件应用到集群：
   kubectl apply -f k8s/mysql-statefulset.yaml
   kubectl apply -f k8s/redis-statefulset.yaml
   kubectl apply -f k8s/halo-statefulset.yaml

3. 检查 Pod 与 PVC：
   kubectl -n halo get all
   kubectl -n halo get pvc

4. 配置与验证：
   - Halo 的配置通过环境变量设置了数据库/redis 主机与端口；你也可使用 ConfigMap 或 Secret 来管理 `application.yml`。
   - 为了在多副本下保持会话一致，启用 Spring Session + Redis（添加依赖 `spring-session-data-redis` 并设置 `spring.session.store-type=redis`）。

生产注意事项（建议）：
- 使用托管数据库/缓存或引入 Operator 来确保 HA、备份与恢复、监控。
- 将 DB 密码、Redis 密码放入 `Secret`，不要在 YAML 中明文写入。
- 设置资源请求/限制、探针、PodDisruptionBudget、网络策略、日志驱动和指标导出。
- 考虑使用 HorizontalPodAutoscaler (HPA) 和合理的资源配额。

调试：
- 若出现 R2DBC 连接重置问题：检查 MySQL 端日志、网络策略、内核 TCP 设置（如 keepalive）、以及应用的连接池配置（最大连接数、心跳/验证）。
- 可先在集群中将 Halo 的副本数减为 1 以排除并发/连接池问题，再逐步扩容。
