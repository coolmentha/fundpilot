# syntax=docker/dockerfile:1

# ---- 构建阶段:Maven 打包 ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# 先拷 pom.xml 预下载依赖,单独成层;后续代码变动不重下依赖
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 拷源码,打包跳测试(CI 已验证)
COPY src ./src
RUN mvn -B package -DskipTests

# ---- 运行阶段:JRE 跑 fat jar ----
FROM eclipse-temurin:25-jre

# 非 root 运行
RUN groupadd -r fundpilot && useradd -r -g fundpilot -u 1001 fundpilot
WORKDIR /app

# 装 curl:容器内 actuator/health 探活(CI Smoke test 与运维诊断依赖);temurin-jre 不自带
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# spring-boot-maven-plugin 重打包后的 fat jar;*.original 是重打包前的薄 jar,不匹配 *.jar
COPY --from=build --chown=fundpilot:fundpilot /workspace/target/*.jar /app/app.jar

# JAVA_OPTS 注入位:时区(@Scheduled 按 A 股时段设计,容器默认 UTC 会错位 8 小时)
# GraalVM Polyglot JS 引擎在 JRE 上跑解释器模式,无需额外 native-access 参数
ENV JAVA_OPTS="-Duser.timezone=Asia/Shanghai" \
    TZ=Asia/Shanghai

EXPOSE 8080
USER fundpilot
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
