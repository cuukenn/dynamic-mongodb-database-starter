# AGENTS.md

## 项目简介

`dynamic-mongodb-database-starter` 是一个 Spring Boot 多数据源 MongoDB 启动器：通过 `@DynamicMongo` 注解在运行时切换 MongoClient 实例与数据库名。

- 技术栈：Java 8 / Maven 单模块 / Spring Boot 2.5.14 parent / MIT 协议
- 有测试：`src/test` 下 14 个 JUnit 5 单元测试类、共 79 个测试，**纯单元测试、不依赖 Mongo 实例**（`MongoClient` 用 Mockito mock），不要引入需要真实 Mongo 的测试

## 构建与验证

```bash
mvn -B test                    # 跑测试（推荐的最低验证标准）
mvn -B package -DskipTests     # 只打 jar
mvn -B -P release validate     # 校验发布 profile（发布相关改动后跑一次）
```

- 没有 Maven Wrapper（无 `mvnw`/`.mvn`）：CI 用 runner 自带的 Maven，本地若 `mvn` 不在 PATH，可用 IDE 自带的那份，例如
  `/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn`（版本随 IDE 变化）。
- 改动运行期逻辑时，先写测试再看它失败（红→绿）。测试支撑：`MethodInvocationStub`（构造 `MethodInvocation`，不必起容器）、`ApplicationContextRunner` / `WebApplicationContextRunner`（装配）、真实 `AnnotationConfigApplicationContext` + `@EnableAspectJAutoProxy`（代理行为）。
- 测试布局：`DynamicMongoAutoConfigurationTest`（装配顺序/条件，含 `ReactiveWebApplicationContextRunner` 的 WebFlux 分支）、`JdkProxyAnnotationResolutionTest` + `InheritedAnnotationResolutionTest`（代理与注解查找，**这两类是最容易静默失效的地方**）、`DynamicMongoAnnotationInterceptorTest`（栈卫生）、`DynamicMongoDatabaseFactoryTest`（切换/继承/库名优先级/报错，14 条最全）、`DynamicMongoClientBuilderTest`（应用的全局 customizer 是否作用到动态源）、`DynamicMongoContextTaskDecoratorTest` + `DynamicMongoAsyncPropagationTest`（跨线程传播，含正反两条对照）、`DefaultDynamicMongoContextTest`（快速失败）、`SpElValueParserTest` / `RequestValueParserTest` / `AbstractChainValueParserTest`（取值链与责任链契约）、`DynamicMongoClassResolverTest`（类级/方法级字段级合并、`allowedPublicOnly`）、`SimpleDynamicMongoClientFactoryTest`（生命周期与失败清理）。

## 硬约束

1. **Java 8 语法**：不得使用 var、record、switch 表达式、text block 等 9+ 特性。
2. **Spring Boot 2.5.x 兼容**：parent 锁定 2.5.14，新 API 需确认在 2.5 可用。
3. **optional 依赖**：`spring-web`、`javax.servlet-api` 是 optional，相关代码只能在 Servlet Web 环境装配（现有代码用 `@ConditionalOnWebApplication` 保护，新增类似代码照做）。
4. **自动配置双注册**：自动配置类注册在两个文件里，改名/移动必须**同时**更新：
   - `src/main/resources/META-INF/spring.factories`（Boot ≤2.6）
   - `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（Boot ≥2.7）
5. **行尾与缩进**：`.editorconfig` 规定 `end_of_line = lf`、缩进 2 空格（`.java` 4 空格）。**仓库已全仓归一为 LF，并由 `.gitattributes`（`* text=auto eol=lf`）强制**——历史 CRLF 文件已一次性转换（内容零改动，用 `git diff --ignore-cr-at-eol` 验证过）。不要再引入 CRLF：那会让每次改动都产生整文件 diff。二进制类型已在 `.gitattributes` 里标 `binary`。
6. **发布流程**：打 GitHub release 触发 `.github/workflows/publish.yml` 发布 Maven Central（JDK 8 + GPG）。注意 pom 里 `autoPublish=false`：上传后产物停在 Central Portal，需要人工点 Publish，并非全自动。

## 架构总览

一次 `@DynamicMongo` 调用的链路：

```
@DynamicMongo 注解方法
→ DynamicMongoAnnotationAdvisor（点cut：类注解 ∪ 方法注解）
→ DynamicMongoAnnotationInterceptor（push 上下文 → 执行 → finally poll）
→ DynamicMongoClassResolver（解析注解为上下文，方法优先于类，带缓存）
→ DefaultDynamicMongoContext.parseValue（#header/#session/SpEL 责任链解析值；Servlet 应用才是完整链，非 Web 应用只有 SpEL）
→ DynamicMongoDatabaseContextHolder（ThreadLocal 栈，支持嵌套切换）
→ AbstractDynamicMongoDatabaseFactory（peek 栈顶，按 instanceId 取客户端、按 database 切库；库名未给时用默认源的库名）
→ SimpleDynamicMongoClientFactory（启动时按 spring.data.mongodb.dynamic.config 预构建的 MongoClient 池）
```

装配入口：`DynamicMongoAutoConfiguration`（开关 `spring.data.mongodb.dynamic.enabled`，默认开）→ `@Import` 三个配置类（`configurate` 包）。装配顺序：`@AutoConfigureAfter(MongoAutoConfiguration)` + `@AutoConfigureBefore(MongoDataAutoConfiguration)`——**必须显式声明**，否则和 Boot 抢 `MongoDatabaseFactory` 的胜负只靠类名字母序。

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

**改动某个模块前，先加载项目 skill `dynamic-mongodb-database-starter`**（`.opencode/skill/dynamic-mongodb-database-starter/SKILL.md`），里面有每个模块的关键类、调用关系和改动注意点。

## 对外文档

- `README.md`（中文，面向使用者）/ `README.en.md`（英文）：内容必须与真实行为一致，改配置项、注解语义或解析规则时同步更新。
- 已知的用户可见陷阱（README 里已写明，改动时勿删）：动态值解不出来抛 `IllegalStateException` 而不是回落默认源；不写 `databaseName` 时库名按「外层上下文 → 该动态源 `database` → 默认源库名」取；切换不跨线程（在 `@DynamicMongo` 方法内部把活儿交给别的线程会丢上下文，需声明 `DynamicMongoContextTaskDecorator`）。
- 注解查找范围必须与点cut 一致：**类、父类、接口、方法**上的注解都要生效，且方法级优先于类级。用 `AnnotationUtils.getAnnotation`（`INHERITED_ANNOTATIONS`，不搜接口）会漏掉接口上的注解，造成「通知执行了但上下文是空的」静默失效——必须用 `AnnotatedElementUtils.findMergedAnnotation`。

## 已知问题（改动前先看）

已修的（都带测试，别改回去）：
1. **自动配置排序**（原：靠隐式约定）：`DynamicMongoAutoConfiguration` 与 Boot 的 `MongoDataAutoConfiguration`（其 `MongoDatabaseFactoryConfiguration` 是类级 `@ConditionalOnMissingBean(MongoDatabaseFactory.class)`）抢同一个 Bean，现在已显式声明 `@AutoConfigureBefore(MongoDataAutoConfiguration.class)`；`DynamicMongoAutoConfigurationTest.declaresAutoConfigurationOrderBeforeMongoDataAutoConfiguration` 锁住这一点。
2. **非 Web 应用无法启动**（原：直接报 `NoSuchBeanDefinitionException: DynamicContextValueParser`）：`@ConditionalOnWebApplication(ANY)` 的语义是「servlet 或 reactive web 应用」，非 Web 应用两个 Web 分支都不匹配 → 没有取值链 Bean → Advisor 装配失败。`DynamicMongoAopConfiguration` 已补 `@ConditionalOnNotWebApplication` 分支（只给 SpEL），测试见 `providesValueParserOnNotWebApplication`。
3. **两个 parser Bean 的条件在 Servlet 应用里同时命中**（SERVLET 与 ANY），靠方法声明顺序 + `@ConditionalOnMissingBean` 决定谁生效。**SERVLET 分支必须排在 ANY 分支前面**；`providesServletValueParserOnServletWebApplication` 锁住该顺序（故意不把 ANY 收紧成 REACTIVE：那样会让既不是 servlet 也不是 reactive 的 Web 上下文彻底没有取值链，反而引入启动失败风险）。
4. **`aop.enabled` 曾无消费者**：Advisor 原先无条件注册，属性设置无效。现在 `dynamicMongoClientAnnotationAdvisor` 上有 `@ConditionalOnProperty(prefix = DynamicMongoProperties.PREFIX + ".aop", name = "enabled", matchIfMissing = true)`：设为 `false` 时不注册 Advisor（不生成代理、注解拦截失效；Resolver/Parser Bean 仍会装配）。`disablesAnnotationAdvisorWhenAopDisabled` / `keepsAnnotationAdvisorWhenAopEnabledByDefault` 锁住。
5. **JDK 动态代理下注解完全失效**（原：注解写在实现类/实现方法上，Bean 以接口类型暴露时不生效，且不报错）：`invocation.getMethod()` 是接口方法、`getThis().getClass()` 是 `$ProxyN`，原来的解析器只看这两者 → 上下文为空 → 静默连回默认源。现在解析用 `AopUtils.getTargetClass(targetObject)` + `AopUtils.getMostSpecificMethod(method, targetClass)`；`JdkProxyAnnotationResolutionTest`（4 条，含 CGLIB 对照）锁住。**注意 `resolveContext` 的签名从 `(Method)` 变成 `(Method, Class<?>)`——这是被文档化的扩展点上的破坏性变更。**
6. **注解写在父类/接口上时静默失效**（与第 5 条同源，另一条触发路径）：点cut 的类匹配器用 `AnnotationClassFilter(checkInherited=true)`（搜父类+接口），而解析器原先用的 `AnnotationUtils` 不搜接口 → 匹配但上下文为空。已改为 `AnnotatedElementUtils.findMergedAnnotation`；`InheritedAnnotationResolutionTest`（超类方法/接口类级/接口方法级 3 条）锁住。
7. **动态值解不出来时静默回落默认源**（多租户最危险的失败模式）：SpEL 求值为 `null`、header/session 取到空字符串时，原来会变成空 `instanceId` → 用默认源。现在 `DefaultDynamicMongoContext.parseValue` 区分「注解没写」（合法用默认源）与「写了但解不出来」（抛 `IllegalStateException`，消息含原始表达式、字段名、方法签名）；`DefaultDynamicMongoContextTest` 7 条锁住。
8. **SpEL 每次调用重新编译**：`SpElValueParser` 现在按表达式字符串缓存编译结果（`ConcurrentHashMap`，只缓存 `Expression`，**绝不缓存 `EvaluationContext`**；`setParserContext`/`setExpressionParser` 会清缓存）；`SpElValueParserTest.compilesEachExpressionOnlyOnce` 用计数解析器锁住。
9. **`#header`/`#session` 的报错不可用**（原：属性缺失直接 NPE、非请求线程裸 NPE）：现在两个 parser 都抛带原始表达式与 header/属性名的 `IllegalStateException`；`RequestValueParserTest` 7 条锁住。
10. **工厂启动失败漏连接池**：`SimpleDynamicMongoClientFactory.afterPropertiesSet` 原来先给字段赋值再循环，第 N 个源构建失败时前 N-1 个客户端没人关（bean 初始化失败 Spring 不会调 destroy）。现在先建到局部 map，失败时先 `closeClients` 再抛；`closesAlreadyBuiltClientsWhenOneSourceFails` 锁住。
11. **空上下文遮蔽外层上下文**：`DynamicMongoAnnotationInterceptor` 现在只在 `instanceId`/`database` 至少一个有文本时才 push/poll（空注解=不切换）；`emptyContextDoesNotShadowOuterContext` 锁住。另：`equals`/`hashCode`/`toString` 由代理直接处理、不经过切面（`DynamicMongoAnnotationInterceptorTest` 锁住）。
12. **Advisor 无法被下游替换**：`dynamicMongoClientAnnotationAdvisor` 现在带 `@ConditionalOnMissingBean(DynamicMongoAnnotationAdvisor.class)`。**条件必须带类型**：只写 `@ConditionalOnMissingBean` 会按方法返回类型 `Advisor` 判断，容器里出现任意别的 Advisor（Boot 应用里是常态）就会把我们的 Advisor 一起关掉。`backsOffWhenUserDefinesOwnAdvisor` 锁住。
13. **pom 里 `<resources>` 的 `bootstrap*.yml` 过滤是死配置**（原「未修」条目）：本模块 `src/main/resources` 下只有 `META-INF`、没有任何 yml，两段 `<resource>` 都等同 Maven 默认行为，已删除（原地留了注释说明）。将来真需要打包时替换变量再加回来。

14. **动态源的 `database` 字段曾不生效**：现在生效。库名优先级：**注解 `databaseName` → 外层上下文继承 → 该动态源 `dynamic.config.<id>.database` → 默认源的库名**（uri 里的库名**不参与**，避免把数据搬去 uri 库）。`DynamicMongoDatabaseFactoryTest` 的 `usesSourceOwnDatabaseWhenAnnotationHasNoDatabaseName` / `annotationDatabaseWinsOverSourceOwnDatabase` / `fallsBackToDefaultDatabaseNameWhenSourceHasNoDatabase` 锁住。
15. **方法级注解曾整体覆盖类级注解**（只写 `databaseName` 会把 `instanceId` 重置为空 → 默认集群）：现在**字段级合并**——方法级注解只覆盖它自己写了的字段，没写的沿用类级；`DynamicMongoClassResolverTest` 的 `mergesMethodLevelInstanceIdWithClassLevelDatabase` / `databaseOnlyMethodAnnotationKeepsClassLevelInstanceId` 锁住。同时 `DynamicMongoDatabaseContextHolder` 新增 `peekInstanceId()` / `peekDatabase()`：**内层上下文没指定的字段沿用外层**（嵌套调用不再遮蔽外层真实上下文），`DynamicMongoDatabaseFactoryTest` 的两条 `inheritsOuter*` 锁住。
16. **Boot 标准 `MongoClientSettingsBuilderCustomizer` 曾不作用于动态源**（下游用惯用法配 TLS/连接池/集群设置时动态源静默拿不到）：现在 `dynamicMongoClientBuilder` 会消费 `ObjectProvider<MongoClientSettingsBuilderCustomizer>`，并**排除** Boot 为默认源注册的 `MongoPropertiesClientSettingsBuilderCustomizer`（否则默认源 uri 会泄进动态源）。**顺序关键**：先应用动态源自己的 customizer（uri/host/凭据），再应用应用的——`applyConnectionString` 会整体替换集群设置，反过来会把应用设的 `replicaSet` 冲掉。`DynamicMongoClientBuilderTest` 两条锁住。

未修 / 需决策：
17. **刻意不加的东西**：入口不加 `@ConditionalOnClass`（spring-aop / spring-data-mongodb 是硬依赖，条件恒真=死代码）；`Type.ANY` 不收紧成 `REACTIVE`（见第 3 条）。
18. **文档化的已知限制**（不是 bug，但容易被当 bug 报）：切换是 `ThreadLocal`，不跨线程——**在 `@DynamicMongo` 方法内部**把活儿交给别的线程（无注解的 `@Async` 协作者 / `CompletableFuture` / 自建线程池）时池线程看不到上下文，会走默认源；修复方式：声明 `DynamicMongoContextTaskDecorator`（`TaskDecorator`）Bean（Boot 的 `ThreadPoolTaskExecutor` 会自动应用），或用 `snapshot()`/`restore()` 手动传递。**实测**：`@Async` 与 `@DynamicMongo` 写在同一个方法上不受影响——那种写法下异步通知更外层，我们的切面是在池线程上执行的（`DynamicMongoAsyncPropagationTest` 有正反两条对照）。其它限制：不支持 WebFlux/`ReactiveMongoTemplate`；bean 名 `mongoClientFactory` 实际注册的是 `MongoDatabaseFactory`（与 Boot 的 `mongoDatabaseFactory` 命名不一致）；每个源一个连接池（N × `maxPoolSize`）。

## CI（ci.yml）

- 触发：push / PR 到 `master`、`develop`。
- 步骤：`mvn -B verify`（`src/test` 是纯单元测试，不需要 Mongo 实例）+ `mvn -B -P release validate`（确认 release profile 的插件与配置能解析；不发布、不签名）。
- 旧版本是从别的多模块项目拷来的（`-Pmain-module`/`-Pall-modules` 在本仓库不存在，Maven 只打 WARNING 就继续；注释含 examples/JMH/HttpTest），已删除重写。

## 发布链路（publish.yml）

- 触发：GitHub release（`types: [released]`）→ `mvn -B -P release deploy`（Central Portal 插件在 `extensions=true` 下接管 deploy）。
- **`server-id` 必须是 `central`**，与 pom 里 `central-publishing-maven-plugin` 的 `publishingServerId` 一致。
- setup-java 的 `server-username`/`server-password`/`gpg-passphrase` **收的是环境变量名而不是值**：填的是 `OSSRH_USERNAME` / `OSSRH_TOKEN` / `OSSRH_GPG_SECRET_KEY_PASSWORD` 这些**名字**，值在同一步骤的 `env:` 里用 `secrets.OSSRH_*` 注入。secret 名字是既有配置，**不要改**。
- `autoPublish=false`：上传后产物停在 Central Portal，需要人工点 Publish。
- `actions/setup-java@v4` 目前会打印弃用提示（v4 deprecated）；升到后继版本前先确认 `server-id`/`server-username`/`server-password`/`gpg-private-key`/`gpg-passphrase` 这些入参名没变。已从 v4 的 `action.yml` 核实这五个入参存在。

## 代码检索

仓库已初始化 CodeGraph 索引（`.codegraph/`），优先用 `codegraph explore "<符号名或问题>"` 或 MCP `codegraph_explore` 定位代码，而非裸 grep。
