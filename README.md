# dynamic-mongodb-database-starter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.cuukenn/dynamic-mongodb-database-starter.svg)](https://central.sonatype.com/artifact/io.github.cuukenn/dynamic-mongodb-database-starter)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-8%2B-blue.svg)](#环境要求)

Spring Boot 多数据源 MongoDB 启动器。用一个注解 `@DynamicMongo`，在**运行时**把 `MongoTemplate` 切到不同的 MongoDB 实例或不同的库；实例与库名还可以从**请求头、Session 或 SpEL 表达式**里动态取，一个请求用一个租户库。

```java
@DynamicMongo(instanceId = "#headerX-Tenant-Id", databaseName = "#headerX-Tenant-Id")
public List<Order> listOrders() {
    return mongoTemplate.findAll(Order.class);   // 自动落到当前租户的实例与库
}
```

## 特性

- **切实例**：`@DynamicMongo("slave")`，实例对应 `dynamic.config` 里的一个 key
- **切库**：`@DynamicMongo(databaseName = "order_db")`，实例不变只换库
- **动态取值**：`instanceId` / `databaseName` 支持 `#header…`、`#session…`、SpEL `#{…}` 三种写法
- **嵌套切换**：上下文是 ThreadLocal 栈，方法内再切一次，退出后自动还原
- **零侵入**：不配动态源时行为与原生 Spring Boot 完全一致
- **可替换扩展点**：注解解析器、取值解析器、客户端工厂/构建器/定制器都可被下游 Bean 覆盖

## 环境要求

| | 要求 |
| --- | --- |
| JDK | 8 及以上 |
| Spring Boot | 2.5.x（`spring.factories` 注册）/ 2.7+（`AutoConfiguration.imports` 注册） |
| Web | 可选。但 `#header…` / `#session…` 仅在 **Servlet Web 应用**中可用，非 Servlet 环境只有 SpEL |

## 快速开始

### 1. 引入依赖

```xml
<dependency>
  <groupId>io.github.cuukenn</groupId>
  <artifactId>dynamic-mongodb-database-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

最新版本见 [Maven Central](https://central.sonatype.com/artifact/io.github.cuukenn/dynamic-mongodb-database-starter)。

### 2. 配置数据源

`spring.data.mongodb.*` 是**默认源**（原有写法不变）；`spring.data.mongodb.dynamic.config` 下的每个 key 是一个**动态源**，**key 就是注解里要写的 `instanceId`**。动态源的字段与原生 `spring.data.mongodb.*` 完全相同（`uri`、`host`、`port`、`database`、`username`、`password`、`replica-set`…），也支持 `uri` 与 `host` 两种写法。

> ⚠️ **动态源的 `database` 不决定实际库名。** 库名只来自两处：注解上的 `databaseName`；没写 `databaseName` 时用**默认源**（`spring.data.mongodb.*`）的库名。所以 `@DynamicMongo("slave")` 是「slave 的实例 + 默认源的库」。

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://127.0.0.1:27017/default_db     # 默认源
    dynamic:
      config:
        slave:                                      # instanceId = "slave"
          uri: mongodb://127.0.0.1:27017/slave_db
        report:                                     # instanceId = "report"
          host: 10.0.0.2
          port: 27017
          database: report_db                       # 该源的默认库名：注解不写 databaseName 时用它
          username: report_user
          password: 123456
```

动态源的 `MongoClient` 在**应用启动时一次性全部建好**（`SimpleDynamicMongoClientFactory`），应用关闭时统一释放；没有任何动态源时只打一条 warn 日志，一切走默认源。

### 3. 用 @DynamicMongo 切换

注解可标在**方法**或**类**上，**方法上的注解优先于类上的**。

| 写法 | 效果 |
| --- | --- |
| 不标注解 | 默认源（`spring.data.mongodb.*`） |
| `@DynamicMongo("slave")` | `instanceId = slave` 的实例，库用**默认源**的库名 |
| `@DynamicMongo(instanceId = "slave")` | 同上（`value` 与 `instanceId` 互为别名） |
| `@DynamicMongo(databaseName = "order_db")` | 实例不变，库切到 `order_db` |
| `@DynamicMongo(instanceId = "slave", databaseName = "order_db")` | 实例 `slave` + 库 `order_db` |

```java
@Service
public class OrderService {

    @Autowired
    private MongoTemplate mongoTemplate;

    // 不标注解：走默认源
    public List<Order> listFromDefault() {
        return mongoTemplate.findAll(Order.class);
    }

    // 切到 slave 实例，库用默认源（spring.data.mongodb.*）的库名
    @DynamicMongo("slave")
    public List<Order> listFromSlave() {
        return mongoTemplate.findAll(Order.class);
    }

    // 切到 slave 实例，库换成 order_archive
    @DynamicMongo(instanceId = "slave", databaseName = "order_archive")
    public List<Order> listArchived() {
        return mongoTemplate.findAll(Order.class);
    }
}
```

类级注解用于"整个类都走某个源"，方法级再逐个覆盖：

```java
@Service
@DynamicMongo("slave")            // 本类所有方法默认走 slave 实例
public class SlaveOrderService {

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<Order> list() {                    // slave 实例 + 默认源的库
        return mongoTemplate.findAll(Order.class);
    }

    @DynamicMongo("report")                        // 方法级覆盖类级
    public List<Report> listReports() {
        return mongoTemplate.findAll(Report.class);
    }
}
```

> 类级注解**无法在方法上取消**——想让某个方法回到默认源，就把它放到没有类级注解的类里。

### 4. 动态取值：按请求切库

`instanceId` 和 `databaseName` 的**字符串值**会按 `#header` → `#session` → SpEL 的顺序解析（只有 Servlet Web 应用装配了完整的 `#header`/`#session`；其他环境只有 SpEL）：

| 写法 | 含义 |
| --- | --- |
| `#headerX-Tenant-Id` | 取请求头 `X-Tenant-Id`。头名**直接跟在 `#header` 后面**，不加点 |
| `#sessiontenantId` | 取 session 属性 `tenantId`，同样直接跟在 `#session` 后面 |
| `#{@tenantConfig.instanceId}` | SpEL 模板，可引用容器里的 Bean |
| `#{#tenantId}_db` | SpEL 模板，可用方法参数名拼接 |
| 普通字符串（如 `slave`） | 不匹配上面任何前缀时按 SpEL 模板解析，仍是字面量本身 |

```java
// 按请求头切换实例和库：同一个方法服务所有租户
@DynamicMongo(instanceId = "#headerX-Tenant-Id", databaseName = "#headerX-Tenant-Id")
public List<Order> listByTenant() {
    return mongoTemplate.findAll(Order.class);
}
```

```java
// SpEL：从任意 Bean 取值
@DynamicMongo(instanceId = "#{@tenantConfig.currentInstanceId()}")
public void saveOrder(Order order) {
    mongoTemplate.save(order);
}

// SpEL：用方法参数拼库名
@DynamicMongo(databaseName = "#{#tenantId}_db")
public List<Order> listByTenant(String tenantId) {
    return mongoTemplate.findAll(Order.class);
}
```

注意事项：

- **动态值解不出来会直接失败，不会回落到默认源。** `#header…`/`#session…` 取不到值（或取到空字符串）、SpEL 求值结果是 `null`、请求线程之外根本没有 request 时，都会抛：

  ```
  IllegalStateException: cannot resolve instanceId '#headerX-Tenant-Id' on method 'OrderService#listByTenant':
  the dynamic value resolved to null; @DynamicMongo never falls back to the default datasource
  ```

  这是有意的：多租户场景下静默读到默认库比重试更危险。
- `#session…` 要求 session 里存在该属性且非 null，取不到同样抛上面的异常（不会 NPE）。
- SpEL 用方法参数名依赖编译期 `-parameters`（继承 `spring-boot-starter-parent` 时默认开启）；没有参数名时用 `#p0`、`#a0` 这类位置引用。
- 注解可以写在**类、父类或接口**上，也写在**方法**上；方法级优先于类级。

## 配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.data.mongodb.dynamic.enabled` | `true` | 是否启用本 starter |
| `spring.data.mongodb.dynamic.config.<instanceId>` | — | 动态源，字段同 `spring.data.mongodb.*`；key 即注解里的 `instanceId` |
| `spring.data.mongodb.dynamic.aop.order` | `Ordered.HIGHEST_PRECEDENCE` | 切换切面的优先级 |
| `spring.data.mongodb.dynamic.aop.allowed-public-only` | `true` | 只拦截 public 方法 |
| `spring.data.mongodb.dynamic.aop.enabled` | `true` | 设为 `false` 时不注册切面：`@DynamicMongo` 注解不再被拦截（手动 `DynamicMongoDatabaseContextHolder` 切换仍可用） |

## 行为与限制

- 动态源的 `MongoClient` 是**启动时全量创建**的，不是懒加载；配置很多源时启动会稍慢，且**每个源一个独立连接池**（N 个源大致等于 N × `maxPoolSize` 的连接容量，默认 `maxPoolSize` 是 100）。启动失败时会关掉已经建好的客户端，不会漏连接池。
- **库名的优先级**：注解的 `databaseName` → 外层上下文已指定的库名（嵌套调用时）→ 该动态源配置里的 `database` → 默认源（`spring.data.mongodb.*`）的库名。`uri` 里跟的库名**不参与**，避免把数据搬去 uri 里那个库。什么都不配时默认源库名是 `test`；`spring.data.mongodb.uri` 不带库名且没写 `database` 会在启动时报错。
- 切换发生在 `MongoDatabaseFactory` 层，所以 `MongoTemplate`、`MongoRepository` 等所有经由它的访问都会跟随切换。
- **切换基于 `ThreadLocal`，不跨线程传递。** 具体地说：在 `@DynamicMongo` 方法内部把活儿交给别的线程（调用没有注解的 `@Async` 协作者、`CompletableFuture.supplyAsync`、自己 submit 到线程池），那个线程看不到上下文，会走默认源。要让它跟着任务走，声明一个 `TaskDecorator` Bean（Boot 的 `ThreadPoolTaskExecutor` 会自动应用它）：
  ```java
  @Bean
  public TaskDecorator dynamicMongoContextTaskDecorator() {
      return new DynamicMongoContextTaskDecorator();
  }
  ```
  用其它线程池或 `CompletableFuture` 时，用 `DynamicMongoDatabaseContextHolder.snapshot()` / `restore(...)` 手动传递，或沿用 `push()`/`poll()` 配对。
- 只支持阻塞式 `MongoDatabaseFactory`；WebFlux / `ReactiveMongoTemplate` 不在支持范围内（没有 `ReactiveMongoDatabaseFactory` 的切换实现）。
- 同一个事务内跨实例切换没有做特殊适配，请自行评估。
- 类级注解不能被方法级"取消"（见上文）；方法级注解是**字段级合并**：它只覆盖自己写了的字段，没写的沿用类级注解（例如类级写 `instanceId`、方法级只写 `databaseName`，结果仍是"类级的实例 + 方法级的库"）。嵌套调用同理——内层没指定的字段沿用外层上下文。
- 类级注解意味着**该类所有 public 方法**都被切面拦截（包括不访问 Mongo 的方法），每次调用都会解析一次上下文；`equals`/`hashCode`/`toString` 由代理直接处理，不经过切面。
- 只依赖 `spring-data-mongodb`；`spring-web` 与 `javax.servlet-api` 在本 starter 里是 optional，`#header`/`#session` 相关代码只在 Servlet Web 环境装配。

## 扩展点

下列 Bean 都是 `@ConditionalOnMissingBean`：声明同类型的 Bean 即可整体替换默认实现。

| Bean | 作用 | 默认实现 |
| --- | --- | --- |
| `DynamicMongoContextResolver` | 注解 → 上下文 的解析策略（带注解缓存） | `DynamicMongoClassResolver` |
| `DynamicContextValueParser` | 取值责任链 `#header → #session → SpEL` | 三个 Parser 串成的链 |
| `DynamicMongoClientFactory` | `instanceId` → `MongoClient` | `SimpleDynamicMongoClientFactory` |
| `DynamicMongoClientBuilder` | `instanceId` + `MongoProperties` → `MongoClient` | 复用 Boot 的 `MongoClientFactory` |
| `DynamicMongoClientSettingsCustomizer` / `DynamicMongoClientPropertiesCustomizer` | 建客户端前的定制钩子 | 用配置项填充 client settings |
| `MongoDatabaseFactory` | 数据源切换点 | `DynamicMongoDatabaseFactory` |

应用里用 Boot 惯用法声明的 `MongoClientSettingsBuilderCustomizer`（TLS、连接池、集群设置等）也会作用到**动态源**客户端，无需额外配置；Boot 为默认源自动注册的那一个不会被带过去。

## FAQ

**Q：报 `no dynamic mongo database instance found for instanceId 'xxx'`？**
A：解析出的 `instanceId` 在 `spring.data.mongodb.dynamic.config` 里没有对应 key。报错信息里会列出**已经配好的实例**（或提示配置为空），照它补配置即可。

**Q：报 `cannot resolve instanceId '#header…' …`？**
A：说明这个动态值没解出来（头/session 里没有、取到空值，或者不在请求线程上）。这是有意的快速失败——不会静默回落到默认源。

**Q：`@DynamicMongo` 不生效？**
A：依次检查：① 类必须是 Spring 管理的 Bean；② 方法必须是 `public`（`aop.allowed-public-only` 默认只拦 public）；③ 同类内部方法自调用不经过代理，不会生效；④ `spring.data.mongodb.dynamic.enabled` 没有被设为 `false`；⑤ 如果注解写在**接口或父类**上，本 starter 支持（注解写在实现类上同样支持）；⑥ 如果是 `@Async`/线程池里的调用，见上文「行为与限制」的线程边界说明。

**Q：怎么彻底关掉注解切换？**
A：设 `spring.data.mongodb.dynamic.aop.enabled=false`（默认 `true`）：不再注册切面，`@DynamicMongo` 不会被拦截，动态取值（`#header…` 等）也不会解析。手动 `DynamicMongoDatabaseContextHolder.push()/poll()` 的切片仍然有效。若还在切库，先确认这个配置真的被读到了（前缀是 `spring.data.mongodb.dynamic.aop.enabled`）。

**Q：能按请求切到不同的账号/集群吗？**
A：动态源的字段就是 `MongoProperties`，`uri`、`username`、`password`、`host` 都可以按源配，等于每个源一个独立连接。

**Q：`@DynamicMongo("slave")` 不写 `databaseName` 时会连到哪个库？**
A：`instanceId` 只换实例，不换库。不写 `databaseName` 时库名按优先级取：外层上下文 → 该动态源配置里的 `database` → 默认源的库名；要明确换库就直接写 `databaseName`。

## License

[MIT](LICENSE) · English doc: [README.en.md](README.en.md)
