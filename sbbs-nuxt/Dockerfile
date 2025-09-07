# 多阶段构建
# 第一阶段：构建阶段
FROM node:18-alpine AS builder

# 设置工作目录
WORKDIR /app

# 复制 package.json 和 package-lock.json
COPY package*.json ./

# 安装依赖
RUN npm ci --only=production

# 复制源代码
COPY . .

# 构建应用
RUN npm run build

# 第二阶段：生产阶段
FROM node:18-alpine AS runner

# 设置工作目录
WORKDIR /app

# 安装必要的依赖
RUN apk add --no-cache \
    tzdata \
    curl

# 设置时区
ENV TZ=Asia/Shanghai

# 复制构建产物
COPY --from=builder /app/.output ./.output
COPY --from=builder /app/package.json ./package.json

# 暴露端口
EXPOSE 3000

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:3000 || exit 1

# 启动应用
CMD ["node", ".output/server/index.mjs"]