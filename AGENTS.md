# AGENTS.md

## 项目简介

`dynamic-mongodb-database-starter` 是一个 Spring Boot 多数据源 MongoDB 启动器：通过 `@DynamicMongo` 注解在运行时切换 MongoClient 实例与数据库名。

- 技术栈：Java 8 / Maven 单模块 / Spring Boot 2.5.14 parent / MIT 协议
- 无测试代码（`src` 下只有 `main`）

## 构建与验证

```bash
mvn compile        # 快速验证编译
mvn package        # 打 jar（无测试可跳过 -DskipTests）
```

改动后的最低验证标准：`mvn compile` 通过。

## 硬约束

1. **Java 8 语法**：不得使用 var、record、switch 表达式、text block 等 9+ 特性。
2. **Spring Boot 2.5.x 兼容**：parent 锁定 2.5.14，新 API 需确认在 2.5 可用。
3. **optional 依赖**：`spring-web`、`javax.servlet-api` 是 optional，相关代码只能在 Servlet Web 环境装配（现有代码用 `@ConditionalOnWebApplication` 保护，新增类似代码照做）。
4. **自动配置双注册**：自动配置类注册在两个文件里，改名/移动必须**同时**更新：
   - `src/main/resources/META-INF/spring.factories`（Boot ≤2.6）
   - `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（Boot ≥2.7）
5. **发布流程**：打 GitHub release 触发 `.github/workflows/publish.yml` 自动发布 Maven Central（JDK 8 + GPG）。

## 架构总览

一次 `@DynamicMongo` 调用的链路：

```
@DynamicMongo 注解方法
→ DynamicMongoAnnotationAdvisor（点cut：类注解 ∪ 方法注解）
→ DynamicMongoAnnotationInterceptor（push 上下文 → 执行 → finally poll）
→ DynamicMongoClassResolver（解析注解为上下文，方法优先于类，带缓存）
→ DefaultDynamicMongoContext.parseValue（#header/#session/SpEL 责任链解析值）
→ DynamicMongoDatabaseContextHolder（ThreadLocal 栈，支持嵌套切换）
→ AbstractDynamicMongoDatabaseFactory（peek 栈顶，按 instanceId 取客户端、按 database 切库）
→ SimpleDynamicMongoClientFactory（启动时按 spring.data.mongodb.dynamic.config 预构建的 MongoClient 池）
```

装配入口：`DynamicMongoAutoConfiguration`（开关 `spring.data.mongodb.dynamic.enabled`，默认开）→ `@Import` 三个配置类（`configurate` 包）。

## 模块地图

包 `io.github.cuukenn.dynamic.database.mongodb` 下：

| 包 | 职责 |
|---|---|
| （根包） | 自动配置入口 `DynamicMongoAutoConfiguration` |
| `configurate` | 三个 Spring 装配配置类（AOP、客户端、客户端工厂） |
| `properties` | 配置属性绑定（`spring.data.mongodb.dynamic.*`） |
| `support` | 核心 API：注解、各扩展点接口 |
| `support.aop` | 注解拦截器 / Advisor / 注解解析器 |
| `support.context` | 上下文对象 + ThreadLocal 栈持有器 |
| `support.context.parser` | 取值责任链：Header / Session / SpEL |
| `support.factory` | MongoClient 工厂实现与动态 DatabaseFactory |

**改动某个模块前，先加载项目 skill `dynamic-mongodb-starter`**（位于 `.opencode/skill/dynamic-mongodb-starter/SKILL.md`），里面有每个模块的关键类、调用关系和改动注意点（例如 `aop.enabled` 是死配置、SpEL 解析器恒匹配必须排在链尾）。

## 代码检索

仓库已初始化 CodeGraph 索引（`.codegraph/`），优先用 `codegraph explore "<符号名或问题>"` 或 MCP `codegraph_explore` 定位代码，而非裸 grep。
