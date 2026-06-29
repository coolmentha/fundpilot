# CI/CD 选 GitHub Actions + GHCR + VPS docker compose

项目此前无 CI/CD(无 `.github/workflows`、无 Dockerfile、无部署脚本)。本 ADR 记录首次建立 CI/CD 的架构决策:CI 跑后端测试 +
前端构建;CD 打 tag 触发,构建镜像推 GHCR,SSH 到 VPS 拉镜像用 docker compose 重启。前后端独立镜像,前端 Nginx 反代后端,数据库在
VPS 上用容器化 PostgreSQL。

VPS 上 443 端口已被既有服务 `sub2api-caddy`(Caddy 2)占用,无法再绑端口。FundPilot 不自建入口,而是复用 sub2api-caddy
作为统一前置反代:按 `Host` 头把 FundPilot 子域名转发到 `fundpilot-frontend:80`。FundPilot 的生产 compose 把 frontend 接入
sub2api 的外部网络 `deploy_sub2api-network`,Caddy 用容器名直连,不占宿主端口。Caddy 自动为子域名签 Let's Encrypt 证书,HTTPS
零配置。

## Considered Options

- **A. GitHub Actions + GHCR + VPS docker compose(已采纳)**:CI 用 `mvn verify` + `npm run build`;CD 打 tag `v*` 触发,
  `docker/build-push-action` 推 `ghcr.io/coolmentha/fundpilot-{backend,frontend}` 双 tag(版本号 + latest),
  `appleboy/ssh-action` 到 VPS 执行 `git checkout <tag>` + `docker compose pull && up -d`。生产 compose
  `deploy/docker-compose.prod.yml` 编排 db/backend/frontend 三件套。
- **B. 服务器本地构建**:VPS 上 `git pull` + `mvn package` + `npm build` 直接跑 jar 和静态文件。简单但 VPS 要装 JDK 25 +
  Node 构建环境,镜像隔离和回滚都不如容器。
- **C. 云容器服务(ACK/TKE)**:镜像推云厂商 Registry,用托管 K8s/容器服务部署。要额外付费且需已有集群,对个人项目过重。
- **D. 只做 CI 不做 CD**:push 时只跑测试,部署全手动。延迟了 CD 决策但没解决部署自动化。

## Consequences

1. **触发分离**:push/PR 只跑 CI(快反馈、不碰生产);打 tag 才触发部署(版本语义清晰,回滚只需重打旧 tag 或改 `.env` 的
   `TAG`)。
2. **时区用容器侧解决,不改 `@Scheduled` 代码**:`@Scheduled` 全部按 A 股时段设计(14:30/21:00 等)但未设 `zone`,容器默认
   UTC 会错位 8 小时。生产 compose 注入 `TZ=Asia/Shanghai` + `JAVA_OPTS=-Duser.timezone=Asia/Shanghai`,不改业务代码。更彻底的做法是给
   `@Scheduled` 加 `zone="Asia/Shanghai"`,留作 follow-up。
3. **前端独立 Nginx 镜像**:前端 API 调用用纯相对路径 `/api/...`(见 `frontend/src/api/client.js`),无环境变量。生产由 Nginx
   把 `/api/` 反代到 compose 内 `backend:8080`,其余走 SPA fallback。无需前端注入构建期环境变量,匹配现有代码设计。
4. **复用 sub2api-caddy 作为统一入口**:443 已被 sub2api-caddy 占用,FundPilot 不新建入口层,而是让 Caddy 按 `Host`
   头把子域名转发到 `fundpilot-frontend:80`。FundPilot frontend 只 `expose: 80`(compose 内部网络),不映射宿主端口,由 Caddy
   转入。Caddy 自动签子域名证书,HTTPS 零配置。
5. **跨 compose 网络互联**:FundPilot compose 把 frontend 接入 sub2api 的外部网络 `deploy_sub2api-network`(
   `networks.sub2api-network.external: true`),Caddy 用容器名 `fundpilot-frontend` 直连。FundPilot 自身的 db/backend
   仍留在默认网络,不暴露给 sub2api。
6. **GHCR 私有镜像需 VPS 登录**:VPS 须 `docker login ghcr.io -u coolmentha -p <PAT>` 拉私有镜像;首次推送后需在 GitHub
   Packages 设置镜像可见性并授权 PAT 可读。
7. **GraalVM JS 引擎在 JRE 上跑解释器模式**:后端镜像用 `eclipse-temurin:25-jre`,`js-community` 是纯 Java 实现,无需
   native-access 参数(既有测试报告证实 JDK 25 下 `EastmoneyJsParser` 测试通过)。`JAVA_OPTS` 预留注入位以备后续需要。
8. **Flyway 启动时自动迁移**:backend 启动即跑 Flyway(5 个 V__ 脚本),首次部署空库全量初始化,无需单独迁移步骤;后续新增脚本须向后兼容。
9. **一次性准备由用户配合**:GitHub Secrets(`VPS_HOST`/`VPS_USER`/`VPS_SSH_KEY`/`VPS_PATH`)、VPS 环境(Docker + compose +
   ghcr 登录 + 仓库 clone + `.env` + Caddyfile 追加 fund 子域名块)需用户手动准备,CI/CD 流水线本身不替代。
10. **范围外(留 follow-up)**:代码侧给 `@Scheduled` 加 `zone`、PR 预览环境、数据库定时备份、监控告警。
