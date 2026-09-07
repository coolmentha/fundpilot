# 第三方请求签名配置

养基宝 `fundpilot.yangjibao.secret` 是请求签名秘密，通过运行环境变量 `YANGJIBAO_SECRET` 注入。仓库和构建产物不提供可工作的默认值；缺失、空字符串或全空白均在客户端 Bean 创建时使应用启动失败，错误只标明配置键。该约束适用于生产及本地启动；测试仅使用明确的虚构秘密。

签名协议公开部分保持不变：MD5、UTF-8、小写十六进制、去掉查询参数的请求路径、秒级时间戳，以及 `Request-Time`、`Request-Sign`、`Authorization` 请求头。匿名签名拼接路径、时间戳、秘密；登录态签名在路径与时间戳之间加入授权 token。授权 token 是会话秘密，不是部署配置。第三方 URL 是公开端点，超时和会话 TTL 是普通运行参数。行情客户端的公开请求参数不作为本 Issue 的签名秘密处理；用户登录 Cookie 签名属于独立身份认证配置。

生产沿用仓库根目录的受控 `.env` 或宿主环境，由 `deploy/docker-compose.prod.yml` 将 `YANGJIBAO_SECRET` 传入后端容器。只填写第三方协议认可的秘密，不应任意生成替代值。示例文件保留空值；禁止将真实值提交、写入镜像或输出到日志。Compose 对缺失或空值提前报错，应用另行拒绝全空白值。

轮换时在受控运行环境替换该值，然后按既有发布流程重新创建后端容器，例如 `docker compose -f deploy/docker-compose.prod.yml up -d --force-recreate backend`，并检查现有健康端点和第三方只读请求。单纯 `docker compose restart` 不重新加载容器环境。此处仅描述运维步骤，不自动执行。回滚配置同样需要恢复先前仍有效的受控值并重新创建后端；旧值已被第三方撤销时不可回滚。无需在线轮换或新的秘密管理产品。
