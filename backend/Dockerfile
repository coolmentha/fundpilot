# syntax=docker/dockerfile:1

# 将 fat jar 拆成稳定依赖层与易变应用层，GHCR 可复用未变化的依赖 blob。
FROM eclipse-temurin:25-jre AS extract
WORKDIR /workspace
COPY target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# BusyBox wget 是静态二进制，避免每次构建访问 Ubuntu 安全仓库安装 curl。
FROM busybox:1.37.0 AS busybox

# ---- 运行阶段:JRE 跑 fat jar ----
FROM eclipse-temurin:25-jre

# 非 root 运行
COPY --from=busybox /bin/busybox /usr/local/bin/busybox
RUN groupadd -r fundpilot && useradd -r -g fundpilot -u 1001 fundpilot \
    && ln -s /usr/local/bin/busybox /usr/local/bin/wget
WORKDIR /app

# 发布工作流先用 setup-java 的 Maven 缓存打包;Docker 仅封装分层 fat jar。
COPY --from=extract --chown=fundpilot:fundpilot /workspace/extracted/dependencies/ ./
COPY --from=extract --chown=fundpilot:fundpilot /workspace/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=fundpilot:fundpilot /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=fundpilot:fundpilot /workspace/extracted/application/ ./

# JAVA_OPTS 注入位:时区(@Scheduled 按 A 股时段设计,容器默认 UTC 会错位 8 小时)
# GraalVM Polyglot JS 引擎在 JRE 上跑解释器模式,无需额外 native-access 参数
ENV JAVA_OPTS="-Duser.timezone=Asia/Shanghai" \
    TZ=Asia/Shanghai

EXPOSE 8080
USER fundpilot
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
