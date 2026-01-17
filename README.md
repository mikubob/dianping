# 黑马点评 (HMDP) - 本地生活服务平台

> 基于Spring Boot 3 + Redis + MySQL开发的本地生活服务平台，提供店铺查询、优惠券秒杀、用户动态分享等功能

## 🚀 项目介绍

黑马点评是一个仿照大众点评开发的本地生活服务平台，主要包含以下功能模块：
- 用户登录认证系统
- 店铺查询与附近推荐
- 优惠券发布与秒杀功能
- 用户动态分享与点赞评论
- 关注与粉丝系统

## ✨ 核心特性

- 🔐 **用户认证** - 基于Session和JWT的用户认证机制
- 🏪 **店铺检索** - 支持按条件筛选和分页展示店铺信息
- 💰 **优惠券系统** - 包含普通券和秒杀券两种类型
- 📝 **动态分享** - 用户可发布、浏览动态并互动
- 👥 **社交关注** - 用户间相互关注与动态推送
- ⚡ **高性能缓存** - 使用Redis实现多级缓存架构
- 🔒 **分布式锁** - 基于Redisson实现分布式锁防止超卖

## 🛠️ 技术栈

### 后端技术
- **框架**: Spring Boot 3.2.10
- **数据库**: MySQL 8.0
- **缓存**: Redis 6.0 + Redisson
- **持久层**: MyBatis-Plus 3.5.9
- **工具库**: Hutool 5.8.40
- **安全**: Spring Security（部分功能）

### 缓存策略
- **多级缓存**: 本地缓存 + Redis缓存
- **缓存穿透防护**: 布隆过滤器 + 空值缓存
- **缓存击穿防护**: 互斥锁 + 逻辑过期
- **缓存雪崩防护**: 过期时间随机化

## 📋 系统架构

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Client    │───▶│  Nginx      │───▶│  Tomcat     │
│  Browser    │    │  Load       │    │  Cluster    │
└─────────────┘    │  Balance    │    └─────────────┘
                   └─────────────┘          │
                                            ▼
                                    ┌─────────────┐
                                    │  Spring     │
                                    │  Boot App   │
                                    └─────────────┘
                                            │
                 ┌──────────────────────────┼──────────────────────────┐
                 ▼                          ▼                          ▼
          ┌─────────────┐           ┌─────────────┐           ┌─────────────┐
          │   MySQL     │           │    Redis    │           │  Redisson   │
          │  Database   │           │   Cache     │           │  Lock       │
          └─────────────┘           └─────────────┘           └─────────────┘
```

## 🏗️ 项目结构

```
src
├── main
│   ├── java/com/hmdp
│   │   ├── config          # 配置类
│   │   ├── controller      # 控制器层
│   │   ├── dto             # 数据传输对象
│   │   ├── entity          # 实体类
│   │   ├── interceptor     # 拦截器
│   │   ├── mapper          # MyBatis映射接口
│   │   ├── service         # 业务逻辑层
│   │   │   └── impl        # 业务实现层
│   │   ├── utils           # 工具类
│   │   └── HmDianPingApplication.java  # 启动类
│   └── resources
│       ├── db              # 数据库脚本
│       ├── mapper          # MyBatis XML映射文件
│       └── application.yaml # 配置文件
└── test
    └── java/com/hmdp       # 测试类
```

## 📦 环境要求

- **JDK**: 21+
- **Maven**: 3.6+
- **MySQL**: 8.0+
- **Redis**: 6.0+

## 🚀 快速启动

### 1. 克隆项目

```bash
git clone https://github.com/your-username/hm-dianping.git
cd hm-dianping
```

### 2. 修改配置

修改 `src/main/resources/application.yaml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hmdp?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: your_username
    password: your_password
```

### 3. 初始化数据库

执行 `src/main/resources/db/hmdp.sql` 脚本创建数据库表结构

### 4. 启动项目

```bash
mvn spring-boot:run
```

或者打包后运行：

```bash
mvn clean package
java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
```

### 5. 访问应用

启动完成后访问 http://localhost:8081

## 🔧 主要功能接口

### 用户模块
- `POST /user/login` - 用户登录
- `POST /user/register` - 用户注册
- `GET /user/me` - 获取当前用户信息
- `GET /user/{id}` - 根据ID获取用户信息

### 店铺模块
- `GET /shop/{id}` - 查询店铺详情
- `PUT /shop` - 修改店铺信息
- `POST /shop/query` - 分页查询店铺
- `GET /shop/type/list` - 查询店铺类型

### 优惠券模块
- `POST /voucher` - 发布优惠券
- `GET /voucher/{shopId}` - 查询店铺优惠券
- `POST /voucher-order/seckill/{id}` - 秒杀优惠券

### 动态模块
- `POST /blog/upload` - 上传图片
- `POST /blog` - 发布笔记
- `GET /blog/of/user` - 查询个人笔记
- `GET /blog/of/follow` - 查询关注人笔记
- `GET /blog/hot/{id}` - 查询热门笔记

## 📊 性能优化

### 缓存策略
1. **热点数据缓存** - 将频繁访问的数据缓存到Redis
2. **多级缓存** - 结合本地缓存减少Redis压力
3. **缓存预热** - 在高峰期前预先加载热点数据

### 数据库优化
1. **索引优化** - 为常用查询字段建立合适索引
2. **SQL优化** - 使用MyBatis-Plus减少手写SQL
3. **分页优化** - 避免深分页导致的性能问题

### 并发控制
1. **分布式锁** - 防止高并发下的数据不一致
2. **限流降级** - 防止系统被请求冲垮
3. **异步处理** - 提升系统响应速度

## 🧪 测试

项目包含多种测试类型：
- 单元测试：验证各组件功能正确性
- 集成测试：验证各模块协同工作能力
- 压力测试：验证系统在高并发下的表现

运行测试：
```bash
mvn test
```
