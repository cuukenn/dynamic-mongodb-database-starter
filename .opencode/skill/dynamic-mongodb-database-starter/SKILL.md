---
name: dynamic-mongodb-database-starter
description: Use when maintaining or extending the dynamic-mongodb-database-starter itself - changing @DynamicMongo datasource switching, adding a value parser, touching auto-configuration or properties, or debugging why a dynamic datasource does/doesn't take effect
---

# dynamic-mongodb-database-starter 模块指南

维护本 starter 时，先定位你要改的模块，再读对应章节。每章列出：职责、关键类、调用关系、改动注意点。

## 启动装配链（所有模块的入口）

```
META-INF/spring.factories + AutoConfiguration.imports（双注册，改类名必须同步两处）
→ DynamicMongoAutoConfiguration（根包）
   开关：spring.data.mongodb.dynamic.enabled（默认开，matchIfMissing=true）
   @AutoConfigureAfter(MongoAutoConfiguration)
→ @Import 三个配置类（configurate 包）：
   ├─ DynamicMongoAopConfiguration   Advisor + 注解解析器 + 取值责任链
   ├─ DynamicMongoClientConfiguration  DatabaseFactory + ClientBuilder + SettingsCustomizer
   └─ DynamicMongoClientFactoryConfiguration  SimpleDynamicMongoClientFactory
→ SimpleDynamicMongoClientFactory.afterPropertiesSet()
   按 spring.data.mongodb.dynamic.config 全量预构建 MongoClient；destroy() 时全部关闭
```

## 运行期调用链（数据源如何切换）

```
@DynamicMongo 方法被调用
→ DynamicMongoAnnotationAdvisor（点cut = 类注解 ∪ 方法注解）
→ DynamicMongoAnnotationInterceptor.invoke()
   1. DynamicMongoClassResolver.resolve()  注解 → DefaultDynamicMongoContext
      （方法注解优先于类注解；MethodClassKey 缓存 + 双检锁）
   2. DefaultDynamicMongoContext.parseValue()  值经 DynamicContextValueParser 责任链解析
   3. DynamicMongoDatabaseContextHolder.push()  入 ThreadLocal 栈
   4. proceed()：MongoTemplate → AbstractDynamicMongoDatabaseFactory
      getMongoClient()  peek 栈顶 → instanceId 非空 → SimpleDynamicMongoClientFactory.getDynamicMongoClient(instanceId)
      getMongoDatabase() peek 栈顶 → database 非空 → 切到该库
   5. finally：DynamicMongoDatabaseContextHolder.poll()  出栈
```

## 模块：根包 `mongodb`

- **职责**：自动配置唯一入口 `DynamicMongoAutoConfiguration`（空壳，只做注册）。
- **改动注意**：
  - 本类改名/移动 → 同步 `META-INF/spring.factories` 和 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 两个文件。
  - 三个 `configurate` 配置类是被本类的 `@Import({类字面量,...})` 直接引用的——它们改名/移动需同步更新该 `@Import`（无字符串注册文件）。

## 模块：`configurate` 装配层

| 类 | 装配的 Bean |
|---|---|
| `DynamicMongoAopConfiguration` | `Advisor`（interceptor + `@DynamicMongo` 点cut，order 来自配置）、`DynamicMongoContextResolver`（默认 `DynamicMongoClassResolver`）、`DynamicContextValueParser` 链 |
| `DynamicMongoClientConfiguration` | `MongoDatabaseFactory` → `DynamicMongoDatabaseFactory`；`DynamicMongoClientBuilder`（lambda：先跑 properties customizer，再用 Boot 的 `MongoClientFactory` 建客户端）；默认 `dynamicMongoClientSettingsCustomizer` |
| `DynamicMongoClientFactoryConfiguration` | `DynamicMongoClientFactory` → `SimpleDynamicMongoClientFactory` |

- **取值链装配**（关键）：Servlet Web 环境 → `header → session → SpEL`；非 Servlet（ANY）→ 仅 SpEL。
- **扩展点**：所有 Bean 都是 `@ConditionalOnMissingBean`——下游项目可自定义同类型 Bean 整体替换（自定义 Resolver、ValueParser、ClientFactory、ClientBuilder、Customizer 均可）。
- **改动注意**：新增/调整 Bean 时保持 `@ConditionalOnMissingBean`，不要破坏下游覆盖能力。

## 模块：`properties` 配置属性

- `DynamicMongoProperties`：前缀 `spring.data.mongodb.dynamic`；`config: Map<String, MongoProperties>`（动态源，复用 Boot 的 MongoProperties）；嵌套 `aop`。
- `DynamicMongoAopProperties`：`enabled`（默认 true）、`order`（默认 `HIGHEST_PRECEDENCE`）、`allowedPublicOnly`（默认 true）。
- **已知坑（死配置）**：`aop.enabled` 目前**没有任何代码消费**——Advisor 无条件注册，设置它无效。改 AOP 行为前先确认该字段是否已被接线；修复时在 `DynamicMongoAopConfiguration.dynamicMongoClientAnnotationAdvisor` 上加条件或给 Advisor 加开关。

## 模块：`support` 核心 API（扩展点接口层）

| 类型 | 说明 |
|---|---|
| `@DynamicMongo` | 类/方法级；`value` 与 `instanceId` 互为 `@AliasFor`；`databaseName` 为库名 |
| `DynamicMongoContext` | 上下文接口：`getInstanceId()`/`getDatabase()`/`parseValue()` |
| `DynamicMongoContextResolver` | 注解 → 上下文（可替换，如改解析策略） |
| `DynamicContextValueParser` | 取值器接口：`match()` + `parse()` |
| `DynamicMongoClientFactory` | `@FunctionalInterface`：instanceId → MongoClient |
| `DynamicMongoClientBuilder` | instanceId + MongoProperties → MongoClient |
| `DynamicMongoClientSettingsCustomizer` / `DynamicMongoClientPropertiesCustomizer` | 建客户端前的精细定制钩子 |

- **改动注意**：这些是下游项目的扩展面，改签名/语义 = 破坏兼容，先评估再动。

## 模块：`support.aop` 注解拦截

- `DynamicMongoAnnotationAdvisor`：点cut = `AnnotationMatchingPointcut(类)` ∪ 方法级匹配（含接口桥接方法场景）。
- `DynamicMongoAnnotationInterceptor`：push → proceed → finally poll（栈式，支持嵌套切换）。
- `DynamicMongoClassResolver`：`allowedPublicOnly=true` 时非 public 方法直接跳过；`MethodClassKey` 缓存解析结果——**改注解解析逻辑注意缓存不失效问题**（缓存随 Bean 生命周期常驻）。

## 模块：`support.context` + `support.context.parser` 上下文与取值链

- `DefaultDynamicMongoContext`：持有 instanceId/database；`parseValue` 只对非空字段走 parser。
- `DynamicMongoDatabaseContextHolder`：`ThreadLocal<Deque>` 栈（支持 A→B→C 嵌套切换）；`peek/push/poll/clear`；手动 push 必须配对 poll/clear 防泄漏。
- 责任链 `AbstractChainValueParser`：`match()` 命中才 `doParse()`；返回 null 则传给 next。

| Parser | 触发 | 说明 |
|---|---|---|
| `HeaderValueParser` | `#header` 前缀 | 从 request header 取值（依赖 optional 的 spring-web/servlet-api） |
| `SessionValueParser` | `#session` 前缀 | 从 session attribute 取值（同上） |
| `SpElValueParser` | **match() 恒 true（兜底）** | SpEL 模板语法，支持 `@bean`（BeanFactoryResolver）与方法参数引用 |

- **改动注意（关键）**：新增 parser 必须插在 `SpElValueParser` **之前**（它恒匹配，排它后面永远不会被执行）；装配在 `DynamicMongoAopConfiguration` 的两个 `@ConditionalOnWebApplication` 方法里。

## 模块：`support.factory` 客户端工厂

- `SimpleDynamicMongoClientFactory`：启动时（`afterPropertiesSet`）按 `dynamic.config` **全量预构建**所有 MongoClient（非懒加载，无配置仅 warn）；`destroy()` 逐个关闭（吞 RuntimeException 只打日志）。
- `AbstractDynamicMongoDatabaseFactory`（继承 Boot 的 `SimpleMongoClientDatabaseFactory`）：重写 `getMongoClient()`/`getMongoDatabase()` 实现切换——peek 栈顶上下文，instanceId 非空从动态工厂取，否则回落默认客户端；database 非空则切库。
- **改动注意**：加"懒加载/动态刷新"需求改这里（`SimpleDynamicMongoClientFactory` 的 Map 策略 + 生命周期）。

## 全局硬约束

1. Java 8 语法；Spring Boot 2.5.x 兼容。
2. `spring-web`/`javax.servlet-api` 是 optional 依赖——新代码涉及 Web API 必须用 `@ConditionalOnWebApplication` 或等价条件保护。
3. 验证命令：`mvn compile`（无测试代码）。
4. 发布：GitHub release → `publish.yml` → Maven Central。
