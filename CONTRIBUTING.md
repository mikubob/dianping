# 贡献指南 (Contributing Guide)

感谢您有兴趣为黑马点评项目做出贡献！本文档提供了有关如何贡献于此项目的指南。

## 🌳 项目结构

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

## 🛠 开发环境设置

1. **安装依赖**：
   - JDK 21+
   - Maven 3.6+
   - MySQL 8.0+
   - Redis 6.0+

2. **克隆项目**：
   ```bash
   git clone https://github.com/your-username/hm-dianping.git
   cd hm-dianping
   ```

3. **配置数据库**：
   - 创建名为 `hmdp` 的数据库
   - 执行 `src/main/resources/db/hmdp.sql` 初始化表结构

4. **配置Redis**：
   - 修改 `application.yaml` 中的Redis连接信息

5. **启动项目**：
   ```bash
   mvn spring-boot:run
   ```

## 🤝 如何贡献

### 报告 Bug
当您在项目中发现bug时，请遵循以下步骤：

1. 确认该bug尚未被报告（查看 Issues）
2. 创建一个新的 Issue，包含以下信息：
   - 清晰简洁的标题和描述
   - 重现bug的步骤
   - 预期行为和实际行为
   - 您的开发环境信息

### 提出新功能
如果您想为项目提出新功能或改进建议：

1. 确认该功能尚未被提议（查看 Issues）
2. 创建一个新的 Issue，详细描述您的想法
3. 解释为什么这个功能对项目有用

### 提交 Pull Request
1. Fork 项目
2. 从 `master` 分支创建您的功能分支 (`git checkout -b feature/AmazingFeature`)
3. 确保您的代码遵循项目规范
4. 提交您的更改 (`git commit -m 'Add some AmazingFeature'`)
5. 推送到分支 (`git push origin feature/AmazingFeature`)
6. 开启一个 Pull Request

## 📋 代码规范

### Java 代码规范
- 类名使用帕斯卡命名法 (PascalCase)
- 方法名和变量名使用驼峰命名法 (camelCase)
- 常量使用全大写下划线分隔命名法 (UPPER_SNAKE_CASE)
- 保持代码缩进一致（使用4个空格）

### Git 提交规范
- 提交信息应清晰描述变更内容
- 使用祈使句（如 "Add feature" 而不是 "Added feature" 或 "Adds feature"）
- 提交信息首行不超过50个字符
- 如果有必要，在首行之后空一行，然后是详细的描述

### 示例：
```
Add user authentication module

- Implement login and logout functionality
- Add JWT token generation and validation
- Create user session management
```

## 🧪 测试

在提交代码之前，请确保：

1. 所有现有测试都能通过
2. 为新增功能编写适当的单元测试
3. 测试覆盖率不应显著降低

运行测试：
```bash
mvn test
```

## 📖 文档

- 为新功能或API更改提供相应的文档
- 更新README.md（如果必要）
- 在代码中添加适当的注释和JavaDoc

## 🙏 感谢

感谢您花时间阅读此贡献指南，并考虑为黑马点评项目做出贡献。您的贡献使该项目变得更好！