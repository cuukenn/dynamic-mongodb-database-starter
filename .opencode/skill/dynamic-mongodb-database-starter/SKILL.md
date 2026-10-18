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
   @AutoConfigureAfter(MongoAutoConfiguration) + @AutoConfigureBefore(MongoDataAutoConfiguration)
   ↑ 后者必须显式声明：双方都用 @ConditionalOnMissingBean(MongoDatabaseFactory.class) 抢同一个 Bean，
     只写 After 时胜负靠 Boot 排序的类名字母序兜底（隐式，会静默失效）
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
      getMongoClient()  peekInstanceId() → 最近的非空 instanceId → SimpleDynamicMongoClientFactory.getDynamicMongoClient(instanceId)；都没有 → 默认客户端
      getMongoDatabase() peekDatabase() → 最近的非空 database → 切到该库；都没有 → instanceDatabase() 取该动态源自己的 database，再没有 → 用默认源（spring.data.mongodb.*）的库名
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
| `DynamicMongoClientConfiguration` | `MongoDatabaseFactory` → `DynamicMongoDatabaseFactory`（带 `DynamicMongoProperties`，用于取各源的默认库名）；`DynamicMongoClientBuilder`（lambda：**先**动态源自己的 properties/settings customizer（uri/host/凭据，`applyConnectionString` 会整体替换集群设置，必须在前面），**再**应用声明的 `MongoClientSettingsBuilderCustomizer`，但过滤掉 Boot 给默认源注册的 `MongoPropertiesClientSettingsBuilderCustomizer`）；默认 `dynamicMongoClientSettingsCustomizer` |
| `DynamicMongoClientFactoryConfiguration` | `DynamicMongoClientFactory` → `SimpleDynamicMongoClientFactory` |

- **取值链装配**（关键）：Servlet Web 环境 → `header → session → SpEL`（`@ConditionalOnWebApplication(SERVLET)`）；其他 Web 环境（`Type.ANY`，含 reactive）→ 仅 SpEL；非 Web 应用（`@ConditionalOnNotWebApplication`）→ 仅 SpEL。
  - **三个分支缺一不可**：Boot 的 `Type.ANY` 只表示「servlet 或 reactive 的 Web 应用」，非 Web 应用（`WebApplicationType.NONE`）不匹配任何 Web 分支。少一个分支 → 没有 `DynamicContextValueParser` Bean → `Advisor` 装配失败 → **应用启动直接报错**。
  - **SERVLET 分支必须声明在 ANY 分支之前**：Servlet 应用里两个条件同时命中，靠 `@ConditionalOnMissingBean` + 方法声明顺序决定谁生效。改动顺序会被 `DynamicMongoAutoConfigurationTest.providesServletValueParserOnServletWebApplication` 抓住。
  - 故意**不**把 ANY 收紧成 REACTIVE：那样既不是 servlet 也不是 reactive 的 Web 上下文就彻底没有取值链了。
- **扩展点**：所有 Bean 都是 `@ConditionalOnMissingBean`——下游项目可自定义同类型 Bean 整体替换（自定义 Resolver、ValueParser、ClientFactory、ClientBuilder、Customizer、Advisor 均可）。**Advisor 的条件必须带类型**（`@ConditionalOnMissingBean(DynamicMongoAnnotationAdvisor.class)`）：只写 `@ConditionalOnMissingBean` 会按返回类型 `Advisor` 判断，容器里有任意别的 Advisor 就会把我们的关掉。
- **改动注意**：新增/调整 Bean 时保持 `@ConditionalOnMissingBean`，不要破坏下游覆盖能力。

## 模块：`properties` 配置属性

- `DynamicMongoProperties`：前缀 `spring.data.mongodb.dynamic`；`config: Map<String, MongoProperties>`（动态源，复用 Boot 的 MongoProperties）；嵌套 `aop`。
- `DynamicMongoAopProperties`：`enabled`（默认 true）、`order`（默认 `HIGHEST_PRECEDENCE`）、`allowedPublicOnly`（默认 true）。
- **`aop.enabled` 开关（已接线）**：`spring.data.mongodb.dynamic.aop.enabled=false` 时 `DynamicMongoAopConfiguration.dynamicMongoClientAnnotationAdvisor` 上的 `@ConditionalOnProperty(prefix = DynamicMongoProperties.PREFIX + ".aop", name = "enabled", matchIfMissing = true)` 生效 → 不注册 Advisor → 不生成代理、注解拦截失效（Resolver/Parser Bean 仍在）。测试：`DynamicMongoAutoConfigurationTest.disablesAnnotationAdvisorWhenAopDisabled` 与 `keepsAnnotationAdvisorWhenAopEnabledByDefault`。

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

- `DynamicMongoAnnotationAdvisor`：点cut = `AnnotationMatchingPointcut(类)` ∪ 方法级匹配（含接口桥接方法场景）。类分支的方法匹配器是 `MethodMatcher.TRUE`（类上所有 public 方法都拦），`equals`/`hashCode`/`toString` 由代理自身处理、不进切面。
- `DynamicMongoAnnotationInterceptor`：push → proceed → finally poll（栈式，支持嵌套切换）；**只在 instanceId/database 至少一个有文本时才 push/poll**（空上下文=不切换，避免遮蔽外层上下文）。
- `DynamicMongoClassResolver`：`allowedPublicOnly=true` 时非 public 方法直接跳过；`MethodClassKey`（key 用 **`AopUtils.getTargetClass(targetObject)`**，JDK 代理下不能是 `$ProxyN`）缓存注解**原始表达式**，每次 `resolve` 返回**独立副本**（走 `DynamicMongoContext.copy()`——**自定义上下文实现必须覆盖它并返回自身类型**，否则会被降级成 `DefaultDynamicMongoContext`、自定义 `parseValue` 永不生效）——`parseValue` 只允许改写副本，绝不能把解析结果写回共享缓存（否则动态取值只在首次调用生效）。注解查找用 `AnnotatedElementUtils.findMergedAnnotation`，范围必须与点cut 一致（类/父类/接口/方法）；方法级与类级注解**按字段合并**（方法级只覆盖它写了的字段），查找顺序：`resolveContext` 里先取方法级（most-specific method → 接口方法）再取类级（targetClass → declaringClass）。
  - **目标类必须取 `AopUtils.getTargetClass(targetObject)`**（JDK 动态代理的 `getClass()` 是 `$ProxyN`，注解不在它上面），方法用 `AopUtils.getMostSpecificMethod(method, targetClass)` 还原到实现方法。
  - 注解查找用 **`AnnotatedElementUtils.findMergedAnnotation`**，顺序 = 最具体方法 → 原方法 → `targetClass` → `method.getDeclaringClass()`。**必须与点cut 的 `AnnotationClassFilter(checkInherited=true)` 的范围一致**（类/父类/接口/方法）；用 `AnnotationUtils` 会漏接口注解 → 点cut 匹配但上下文为空。
  - `resolveContext(Method, Class<?>)` 的签名是**公开扩展点**（protected），2022 年那轮从 `(Method)` 改过来，子类需要跟着改。

## 模块：`support.context` + `support.context.parser` 上下文与取值链

- `DefaultDynamicMongoContext`：持有 instanceId/database；`parseValue` 只对**有文本**的字段走 parser（注解没写=合法用默认源）。
  - **快速失败（别改回去）**：字段有值但**解不出来**（parser 返回 null 或空串）时抛 `IllegalStateException`（消息含原始表达式、字段名、方法签名），**绝不回落默认源**——静默读错库比失败危险得多。
- `DynamicMongoDatabaseContextHolder`：`ThreadLocal<Deque>` 栈（支持 A→B→C 嵌套切换）；`peek/push/poll/clear` + `peekInstanceId()/peekDatabase()`（从内向外找第一个非空值，路由判断必须用这两个而不是 `peek`）+ `snapshot()/restore(List)`（跨线程传递上下文用）；手动 push 必须配对 poll/clear 防泄漏；**不跨线程**。
- `DynamicMongoContextTaskDecorator`：可选的跨线程补丁（`implements TaskDecorator`），在提交线程抓 `snapshot()`、在执行线程 `restore()` 并在 finally 还原 worker 原有上下文；提交时没有上下文就不包装（不能污染 worker）。**starter 不自动注册**（是否让上下文跟随任务是应用策略），下游自己声明 `@Bean TaskDecorator` 即可（Boot 的 `ThreadPoolTaskExecutor` 自动应用）。注意：`@Async` 与 `@DynamicMongo` 写在同一个方法上本来就没问题（实测异步通知更外层、我们的切面在池线程执行）；真正会丢上下文的是「在 `@DynamicMongo` 方法内部把活儿交给别的线程」，`DynamicMongoAsyncPropagationTest` 有正反两条对照。
- 责任链 `AbstractChainValueParser`：`match()` 命中才 `doParse()`；返回 null 则传给 next。

| Parser | 触发 | 说明 |
|---|---|---|
| `HeaderValueParser` | `#header` 前缀 | 从 request header 取值（依赖 optional 的 spring-web/servlet-api）；取不到/非请求线程抛带表达式的 `IllegalStateException` |
| `SessionValueParser` | `#session` 前缀 | 从 session attribute 取值（同上）；属性缺失同样抛异常（不再 NPE） |
| `SpElValueParser` | **match() 恒 true（兜底）** | SpEL 模板语法，支持 `@bean`（BeanFactoryResolver）与方法参数引用；按表达式字符串**缓存编译结果**（`expressionCache`，只缓存 `Expression`，不缓存 `EvaluationContext`；`setParserContext`/`setExpressionParser` 会清空缓存） |

- **改动注意（关键）**：新增 parser 必须插在 `SpElValueParser` **之前**（它恒匹配，排它后面永远不会被执行）；装配在 `DynamicMongoAopConfiguration` 的三个取值链 Bean 方法里（SERVLET 完整链 / ANY 仅 SpEL / 非 Web 仅 SpEL），新增分支同样要保持 `@ConditionalOnMissingBean`。

## 模块：`support.factory` 客户端工厂

- `SimpleDynamicMongoClientFactory`：启动时（`afterPropertiesSet`）按 `dynamic.config` **全量预构建**所有 MongoClient（非懒加载，无配置仅 warn）；构建到局部 map，**中途失败会先关掉已建好的客户端再抛**（bean 初始化失败 Spring 不会调 destroy）；`destroy()` 无配置时跳过、有配置则逐个关闭（吞 RuntimeException 但记堆栈）；`getInstanceIds()` 用于报错时列出已配实例。
- `AbstractDynamicMongoDatabaseFactory`（继承 Boot 的 `SimpleMongoClientDatabaseFactory`）：重写 `getMongoClient()`/`getMongoDatabase()` 实现切换——用 `peekInstanceId()`/`peekDatabase()` 取**最近的非空**值（内层没指定就沿用外层，不再遮蔽外层真实上下文）；都没有则回落默认客户端/默认库名。库名最后一级是 `instanceDatabase(instanceId)`：该动态源配置里的 `database`（**uri 里的库名不参与**）。
- **改动注意**：加"懒加载/动态刷新"需求改这里（`SimpleDynamicMongoClientFactory` 的 Map 策略 + 生命周期）；要支持"每个动态源一个默认库"也得改这里（默认库名来源）。

## 全局硬约束

1. Java 8 语法；Spring Boot 2.5.x 兼容。
2. `spring-web`/`javax.servlet-api` 是 optional 依赖——新代码涉及 Web API 必须用 `@ConditionalOnWebApplication` / `@ConditionalOnNotWebApplication` 等条件保护，并保证**非 Web 应用也能启动**（starter 必须能在 `WebApplicationType.NONE` 下装配出全部 Bean）。
3. 验证命令：`mvn -B test`（`src/test` 有 14 个测试类、79 个测试，纯单元测试，不依赖 Mongo 实例——`MongoClient` 用 Mockito mock）。最容易静默失效的两块是代理与注解查找（`JdkProxyAnnotationResolutionTest`、`InheritedAnnotationResolutionTest`）和装配条件（`DynamicMongoAutoConfigurationTest` 的 `ApplicationContextRunner`/`WebApplicationContextRunner`/`ReactiveWebApplicationContextRunner`）。切换/库名优先级/继承的落点在 `DynamicMongoDatabaseFactoryTest`（14 条），跨线程传播在 `DynamicMongoContextTaskDecoratorTest` + `DynamicMongoAsyncPropagationTest`。
4. 发布：GitHub release → `publish.yml` → `mvn -B -P release deploy` → Central Portal；`server-id` 必须是 `central`，setup-java 的三个凭据入参填**环境变量名**、值在同一步骤 `env:` 里注入（secret 名 `OSSRH_*` 是既有配置，不要改）。pom 里 `autoPublish=false`，上传后需在 Central Portal 人工点 Publish。
