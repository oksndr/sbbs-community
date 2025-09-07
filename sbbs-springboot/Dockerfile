# 使用 JDK 8 作为基础镜像
FROM openjdk:8-jre-alpine

# 设置工作目录
WORKDIR /app

# 安装必要的依赖
RUN apk add --no-cache \
    tzdata \
    curl \
    bash

# 设置时区
ENV TZ=Asia/Shanghai

# 复制构建好的 JAR 文件
COPY target/sbbs-springboot-*.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs

# 设置 JVM 参数
ENV JAVA_OPTS="-Xmx512m -Xms256m -Djava.security.egd=file:/dev/./urandom"

# 暴露端口
EXPOSE 61234

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:61234/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]