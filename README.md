# 🚀 SBBS 社区论坛 Docker 部署指南

**项目演示**: https://www.sbbs.top/

本指南专注于应用本身的 Docker 部署，基础设施请自行部署。

## 📋 前置要求

在开始之前，请确保以下服务已正常运行：

1. **PostgreSQL 17** - 数据库服务
2. **Redis** - 缓存服务  
3. **EasyImages2.0** - 图床服务

### 初始化数据库
使用 Navicat 执行数据库脚本初始化数据库结构：

1. 打开 Navicat，连接到 PostgreSQL 服务器
2. 新建查询窗口，执行`sbbs.sql` 文件中的所有 SQL 语句创建表结构

EasyImages2.0 部署请参考：https://github.com/icret/EasyImages2.0

## ⚙️ 1. 配置后端应用

### 修改 application.yml
编辑 `sbbs-springboot/src/main/resources/application-prod.yml`：

```yaml
spring:
  datasource:
    druid:
      driver-class-name: org.postgresql.Driver
      url: jdbc:postgresql://your_postgresql_host:5432/sbbs
      username: sbbs_user
      password: your_postgresql_password
  redis:
    host: your_redis_host
    port: 6379
    database: 0
    password: your_redis_password

image:
  upload:
    url: http://your_easyimages_domain/api/index.php
    token: your_easyimages_token

mail:
  host: smtp.qq.com
  port: 465
  username: your_qq@qq.com
  password: "your_qq_authorization_code"
  from-name: SBBS论坛
  ssl-enabled: true
  tls-enabled: false

sa-token:
  jwt-secret-key: your_custom_jwt_secret_key
```

### 构建后端应用
```bash
# 进入后端目录
cd sbbs-springboot

# 使用 Maven 构建（需要 JDK 8）
mvn clean package -DskipTests

# 构建完成后会在 target 目录生成 JAR 文件
ls target/*.jar
```



## 🎨 2. 配置前端应用

### 修改前端配置文件

#### 1. 修改 api.js
编辑 `sbbs-nuxt/utils/api.js`，将 `example:port` 替换为实际的后端地址：

```javascript
// 将第 86 行
return (process.env.SBBS_API_URL || 'http://example:port');

// 替换为
return (process.env.SBBS_API_URL || 'http://localhost:61234');

// 将第 134 行
return isDev ? (process.env.SBBS_DEV_API_URL || 'http://localhost:12367') : (process.env.SBBS_API_URL || 'http://example:port');

// 替换为
return isDev ? (process.env.SBBS_DEV_API_URL || 'http://localhost:61234') : (process.env.SBBS_API_URL || 'http://localhost:61234');
```

#### 2. 修改 nuxt.config.ts
编辑 `sbbs-nuxt/nuxt.config.ts`，将 `example:port` 替换为实际的后端地址：

```typescript
// 将第 42 行
proxy: `${process.env.SBBS_API_URL || 'http://example:port'}/**`,

// 替换为
proxy: `${process.env.SBBS_API_URL || 'http://localhost:61234'}/**`,

// 将第 134 行
? (process.env.SBBS_API_URL || 'http://example:port'),

// 替换为
? (process.env.SBBS_API_URL || 'http://localhost:61234'),
```

#### 3. 修改 tags.js
编辑 `sbbs-nuxt/stores/tags.js`，将 `example:port` 替换为实际的后端地址：

```javascript
// 将第 41 行
: (process.env.SBBS_API_URL || 'http://example:port')

// 替换为
: (process.env.SBBS_API_URL || 'http://localhost:61234')
```

### 构建前端应用
```bash
# 进入前端目录
cd sbbs-nuxt

# 安装依赖
npm install

# 构建应用
npm run build
```



## 🚀 3. 部署应用

### 使用 Docker Compose（推荐）

可以参考我的Docker Compose, 但不保证一定可以启动, 最好借助ai自己构建. **当然如果您有更方便的构建方法, 欢迎给我提交pr**

```bash
# 启动所有服务
docker-compose -f docker-compose-app.yml up -d

# 查看服务状态
docker-compose -f docker-compose-app.yml ps

# 查看日志
docker-compose -f docker-compose-app.yml logs -f

# 停止服务
docker-compose -f docker-compose-app.yml down
```

**端口规划：**
- 后端端口：61234（容器内部和外部一致）
- 前端端口：3000（容器内部和外部一致）
- 容器间通信：前端通过 `http://yourip:port` 访问后端

## 🎯 访问应用

### 初始化设置
1. 访问 **前端地址**
2. 注册管理员账户（默认为普通用户, 需要自己去数据库中将自己的group_id设置为管理员）
3. 登录后台管理功能
4. 测试发帖、评论等功能

## 📌 管理员操作

### 置顶帖子方法
使用 POST 请求设置帖子置顶：

```bash
POST http://your_ip:61234/v2/admin/pinned?postId=1
```

**请求头：**
- `Authorization: Bearer <管理员token>`

**建议使用 Postman 工具进行测试：**
1. 设置请求方法为 POST
2. 填写 URL：`http://your_ip:61234/v2/admin/pinned?postId=1`
3. 在 Headers 中添加：`Authorization: Bearer <你的管理员token>`
4. 发送请求

## 🔧 管理命令

### Docker Compose 管理命令
```bash
# 查看服务状态
docker-compose -f docker-compose-app.yml ps

# 启动服务
docker-compose -f docker-compose-app.yml up -d

# 停止服务
docker-compose -f docker-compose-app.yml down

# 重启服务
docker-compose -f docker-compose-app.yml restart

# 查看日志
docker-compose -f docker-compose-app.yml logs -f
docker-compose -f docker-compose-app.yml logs -f sbbs-backend
docker-compose -f docker-compose-app.yml logs -f sbbs-frontend

# 重新构建并启动
docker-compose -f docker-compose-app.yml up -d --build
```

## 🐛 故障排除

### 常见问题

#### 1. 后端启动失败
```bash
# 查看后端日志
docker logs sbbs-backend

# 检查端口占用
netstat -tlnp | grep 61234

# 检查数据库连接
docker exec sbbs-backend curl -f your_postgresql_host:5432
```

#### 2. 前端启动失败
```bash
# 查看前端日志
docker logs sbbs-frontend

# 检查端口占用
netstat -tlnp | grep 3000

# 检查后端连接
docker exec sbbs-frontend curl -f http://localhost:61234/api/actuator/health
```

#### 3. 图片上传失败
- 检查 EasyImages 服务是否正常运行
- 验证 API Token 是否正确
- 检查网络连接

#### 4. 数据库连接失败
- 确认 PostgreSQL 服务状态
- 检查数据库连接参数
- 验证网络连通性

### 日志查看
```bash
# 实时查看日志
docker logs -f sbbs-backend
docker logs -f sbbs-frontend

# 查看最近100行日志
docker logs --tail 100 sbbs-backend
docker logs --tail 100 sbbs-frontend

# Docker Compose 日志
docker-compose -f docker-compose-app.yml logs -f
```

## 📝 部署清单

- [ ] PostgreSQL 17 已部署并正常运行
- [ ] Redis 已部署并正常运行
- [ ] EasyImages2.0 已部署并获取 API Token
- [ ] 后端 application.yml 已配置正确的连接信息
- [ ] 后端应用已构建为 Docker 镜像
- [ ] 前端配置文件已修改为正确的后端地址
- [ ] 前端应用已构建为 Docker 镜像
- [ ] 后端容器已启动并正常运行
- [ ] 前端容器已启动并正常运行
- [ ] 能够正常访问前端应用
- [ ] 测试发帖、评论功能正常
- [ ] 测试图片上传功能正常

## 🙏 鸣谢

### 核心技术栈
- **后端框架**: Spring Boot - Java 企业级应用开发框架
- **前端框架**: Nuxt.js - Vue.js 服务端渲染框架  
- **数据库**: PostgreSQL 17 - 开源关系型数据库
- **缓存**: Redis - 内存数据结构存储
- **认证授权**: Sa-Token - 轻量级权限认证框架
- **图片服务**: EasyImages2.0 - 简单图床解决方案

### 开发工具
- **构建工具**: Maven - 项目管理和构建工具
- **包管理器**: npm - Node.js 包管理器
- **容器化**: Docker - 应用容器化部署
- **数据库管理**: Navicat - 数据库管理工具

### 开源社区
- 感谢所有开源项目的贡献者
- 感谢开源社区提供的优质工具和框架
- 感谢所有为项目贡献代码和建议的开发者

### 特别感谢
- 感谢 [EasyImages2.0](https://github.com/icret/EasyImages2.0) 项目提供的图床解决方案
- 感谢 [第一主机](https://www.1idc.net/) 提供的廉价服务器支持
- 感谢所有测试用户的反馈和建议

---

**部署完成后，您就可以通过浏览器访问您的 SBBS 社区论坛了！** 🎉
