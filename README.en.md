# dynamic-mongodb-database-starter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.cuukenn/dynamic-mongodb-database-starter.svg)](https://central.sonatype.com/artifact/io.github.cuukenn/dynamic-mongodb-database-starter)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-8%2B-blue.svg)](#requirements)

A Spring Boot starter for multiple MongoDB datasources. With a single annotation, `@DynamicMongo`, you switch `MongoTemplate` to another MongoDB instance or another database **at runtime** — and the instance id / database name can be resolved dynamically from a **request header, a session attribute, or a SpEL expression**, so one request can hit one tenant's database.

```java
@DynamicMongo(instanceId = "#headerX-Tenant-Id", databaseName = "#headerX-Tenant-Id")
public List<Order> listOrders() {
    return mongoTemplate.findAll(Order.class);   // uses the current tenant's instance and database
}
```

## Features

- **Switch instance**: `@DynamicMongo("slave")`, where `slave` is a key in `dynamic.config`
- **Switch database**: `@DynamicMongo(databaseName = "order_db")`, same instance, another database
- **Dynamic values**: `instanceId` / `databaseName` accept `#header…`, `#session…` and SpEL `#{…}`
- **Nesting**: the context is a ThreadLocal stack, so a nested switch is restored automatically
- **Non-intrusive**: with no dynamic source configured, behaviour is identical to plain Spring Boot
- **Replaceable**: the annotation resolver, value parsers and client factory/builder/customizers are all overridable beans

## Requirements

| | Requirement |
| --- | --- |
| JDK | 8 or later |
| Spring Boot | 2.5.x (registered via `spring.factories`) / 2.7+ (via `AutoConfiguration.imports`) |
| Web | Optional. `#header…` / `#session…` only work in a **Servlet** web application; elsewhere only SpEL is available |

## Getting started

### 1. Add the dependency

```xml
<dependency>
  <groupId>io.github.cuukenn</groupId>
  <artifactId>dynamic-mongodb-database-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

The latest version is on [Maven Central](https://central.sonatype.com/artifact/io.github.cuukenn/dynamic-mongodb-database-starter).

### 2. Configure the datasources

`spring.data.mongodb.*` stays your **default source**. Every key under `spring.data.mongodb.dynamic.config` is a **dynamic source**, and **that key is the `instanceId` you write in the annotation**. A dynamic source accepts exactly the same fields as `spring.data.mongodb.*` (`uri`, `host`, `port`, `database`, `username`, `password`, `replica-set`, …).

> ℹ️ **Database name priority.** `databaseName` on the annotation → the database already set by an enclosing context (nested calls) → the `database` of that dynamic source → the **default source**'s (`spring.data.mongodb.*`) database name. A database in the `uri` path is **not** used (so data never moves to the URI's database). So `@DynamicMongo("slave")` means "the `slave` instance, with `slave`'s own `database` if you configured one".

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://127.0.0.1:27017/default_db     # default source
    dynamic:
      config:
        slave:                                      # instanceId = "slave"
          uri: mongodb://127.0.0.1:27017/slave_db
        report:                                     # instanceId = "report"
          host: 10.0.0.2
          port: 27017
          database: report_db                       # this source's default database (used when the annotation omits databaseName)
          username: report_user
          password: 123456
```

All dynamic `MongoClient`s are built **eagerly at startup** (`SimpleDynamicMongoClientFactory`) and closed on shutdown. With no dynamic source configured, only one warning is logged and everything keeps using the default source.

### 3. Switch with @DynamicMongo

The annotation goes on a **method** or a **class**; **a method-level annotation wins over a class-level one**.

| Usage | Result |
| --- | --- |
| No annotation | Default source (`spring.data.mongodb.*`) |
| `@DynamicMongo("slave")` | Instance `slave`, using the **default source**'s database |
| `@DynamicMongo(instanceId = "slave")` | Same (`value` and `instanceId` are aliases) |
| `@DynamicMongo(databaseName = "order_db")` | Same instance, database `order_db` |
| `@DynamicMongo(instanceId = "slave", databaseName = "order_db")` | Instance `slave` and database `order_db` |

```java
@Service
public class OrderService {

    @Autowired
    private MongoTemplate mongoTemplate;

    // no annotation: the default source
    public List<Order> listFromDefault() {
        return mongoTemplate.findAll(Order.class);
    }

    // instance "slave", with the default source's database
    @DynamicMongo("slave")
    public List<Order> listFromSlave() {
        return mongoTemplate.findAll(Order.class);
    }

    // instance "slave", database switched to order_archive
    @DynamicMongo(instanceId = "slave", databaseName = "order_archive")
    public List<Order> listArchived() {
        return mongoTemplate.findAll(Order.class);
    }
}
```

A class-level annotation is for "everything in this class uses that source", overridden method by method:

```java
@Service
@DynamicMongo("slave")
public class SlaveOrderService {

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<Order> list() {                 // slave instance + default source's database
        return mongoTemplate.findAll(Order.class);
    }

    @DynamicMongo("report")                     // method-level wins
    public List<Report> listReports() {
        return mongoTemplate.findAll(Report.class);
    }
}
```

> A class-level annotation **cannot be cancelled on a method**: to use the default source again, put that method in a class without a class-level annotation.

### 4. Dynamic values: one request, one tenant

The **string values** of `instanceId` and `databaseName` are resolved in the order `#header` → `#session` → SpEL (the full `#header`/`#session` chain is only installed in a Servlet web application; everything else gets SpEL only):

| Value | Meaning |
| --- | --- |
| `#headerX-Tenant-Id` | Request header `X-Tenant-Id`. The header name follows `#header` **directly**, without a dot |
| `#sessiontenantId` | Session attribute `tenantId`, again directly after `#session` |
| `#{@tenantConfig.instanceId}` | SpEL template, may reference beans |
| `#{#tenantId}_db` | SpEL template, may use method parameter names |
| A plain string such as `slave` | Handled by the SpEL template parser and returned as-is |

```java
// One method for every tenant, driven by request headers
@DynamicMongo(instanceId = "#headerX-Tenant-Id", databaseName = "#headerX-Tenant-Id")
public List<Order> listByTenant() {
    return mongoTemplate.findAll(Order.class);
}
```

```java
// SpEL: read from any bean
@DynamicMongo(instanceId = "#{@tenantConfig.currentInstanceId()}")
public void saveOrder(Order order) {
    mongoTemplate.save(order);
}

// SpEL: build the database name from a method argument
@DynamicMongo(databaseName = "#{#tenantId}_db")
public List<Order> listByTenant(String tenantId) {
    return mongoTemplate.findAll(Order.class);
}
```

Notes:

- **A dynamic value that cannot be resolved fails — it never falls back to the default source.** An absent (or empty) `#header…`/`#session…` value, a SpEL expression evaluating to `null`, or being off the request thread all produce:

  ```
  IllegalStateException: cannot resolve instanceId '#headerX-Tenant-Id' on method 'OrderService#listByTenant':
  the dynamic value resolved to null; @DynamicMongo never falls back to the default datasource
  ```

  This is deliberate: in a multi-tenant setup, silently reading the default database is worse than failing.
- `#session…` requires the attribute to exist and be non-null; a missing one raises the same exception (no NPE).
- SpEL parameter names need `-parameters` at compile time (enabled by default when you inherit `spring-boot-starter-parent`); without them use positional references such as `#p0`, `#a0`.
- The annotation may sit on a **class, a superclass or an interface**, as well as on a **method**; a method-level annotation wins over a class-level one.

## Configuration properties

| Property | Default | Description |
| --- | --- | --- |
| `spring.data.mongodb.dynamic.enabled` | `true` | Enable this starter |
| `spring.data.mongodb.dynamic.config.<instanceId>` | — | Dynamic source; same fields as `spring.data.mongodb.*`; the key is the annotation's `instanceId` |
| `spring.data.mongodb.dynamic.aop.order` | `Ordered.HIGHEST_PRECEDENCE` | Order of the switching aspect |
| `spring.data.mongodb.dynamic.aop.allowed-public-only` | `true` | Only intercept public methods |
| `spring.data.mongodb.dynamic.aop.enabled` | `true` | Set to `false` to skip the advisor: `@DynamicMongo` is no longer intercepted (manual `DynamicMongoDatabaseContextHolder` switching still works) |

## Behaviour and limitations

- Dynamic `MongoClient`s are created **eagerly at startup**, not lazily, so many sources mean a slower startup — and **one connection pool per source** (N sources is roughly N × `maxPoolSize` connection capacity; the driver default is 100). If startup fails part-way, the clients already built are closed rather than leaked.
- **Database name priority**: `databaseName` on the annotation → the database set by an enclosing context (nested calls) → that dynamic source's `database` → the **default source**'s (`spring.data.mongodb.*`) database name. A database in the `uri` path plays no part, so data never moves to the URI's database. With nothing configured the default database name is `test`; a `spring.data.mongodb.uri` without a database path and no `database` property fails at startup.
- Switching happens at the `MongoDatabaseFactory` level, so `MongoTemplate` and `MongoRepository` both follow it.
- **Switching is `ThreadLocal`-based and does not cross threads.** Concretely: when a `@DynamicMongo` method hands work to another thread (calling an unannotated `@Async` collaborator, `CompletableFuture.supplyAsync`, or submitting to your own executor), that thread sees no context and hits the default source. To make the context follow the task, declare a `TaskDecorator` bean (Boot's `ThreadPoolTaskExecutor` applies it automatically):
  ```java
  @Bean
  public TaskDecorator dynamicMongoContextTaskDecorator() {
      return new DynamicMongoContextTaskDecorator();
  }
  ```
  For other executors or `CompletableFuture`, pass it explicitly with `DynamicMongoDatabaseContextHolder.snapshot()` / `restore(...)`, or keep pairing `push()`/`poll()`.
- Only the blocking `MongoDatabaseFactory` is supported; WebFlux / `ReactiveMongoTemplate` are out of scope (there is no reactive switching implementation).
- Switching instances inside a single transaction is not specially supported; evaluate it yourself.
- A class-level annotation cannot be cancelled by a method-level one; a method-level annotation is **merged field by field** — it overrides only the fields it sets, and unset fields come from the class-level annotation (e.g. class sets `instanceId`, method sets only `databaseName` → "class's instance + method's database"). The same applies to nested calls: fields an inner annotation leaves out are inherited from the enclosing context.
- A class-level annotation intercepts **every public method** of that class (including methods that never touch Mongo), so each call resolves a context; `equals`/`hashCode`/`toString` are handled by the proxy itself and never reach the aspect.
- Only `spring-data-mongodb` is required; `spring-web` and `javax.servlet-api` are optional here, and the `#header`/`#session` code is only installed in a Servlet web application.

## Extension points

All of the following beans are `@ConditionalOnMissingBean`: declare your own bean of the same type to replace the default.

| Bean | Purpose | Default |
| --- | --- | --- |
| `DynamicMongoContextResolver` | Annotation → context resolution (with an annotation cache) | `DynamicMongoClassResolver` |
| `DynamicContextValueParser` | Value chain `#header → #session → SpEL` | the three chained parsers |
| `DynamicMongoClientFactory` | `instanceId` → `MongoClient` | `SimpleDynamicMongoClientFactory` |
| `DynamicMongoClientBuilder` | `instanceId` + `MongoProperties` → `MongoClient` | reuses Boot's `MongoClientFactory` |
| `DynamicMongoClientSettingsCustomizer` / `DynamicMongoClientPropertiesCustomizer` | Hooks before a client is built | fills client settings from properties |
| `MongoDatabaseFactory` | The switching point | `DynamicMongoDatabaseFactory` |

`MongoClientSettingsBuilderCustomizer` beans you declare the usual Boot way (TLS, connection pool, cluster settings) are applied to **dynamic** clients too — no extra setup — but Boot's own customizer for the default source is deliberately not carried over.

## FAQ

**Q: I get `no dynamic mongo database instance found for instanceId 'xxx'`**
A: The resolved `instanceId` has no matching key in `spring.data.mongodb.dynamic.config`. The message also lists the instances that *are* configured (or tells you the configuration is empty) — add the missing source.

**Q: I get `cannot resolve instanceId '#header…' …`**
A: That dynamic value could not be resolved (absent or empty header/session entry, or off the request thread). This is the intended fail-fast — it never falls back to the default source.

**Q: `@DynamicMongo` has no effect**
A: Check, in order: ① the class is a Spring bean; ② the method is `public` (`aop.allowed-public-only` defaults to public only); ③ self-invocation inside the same class does not go through the proxy; ④ `spring.data.mongodb.dynamic.enabled` is not `false`; ⑤ an annotation on an interface or superclass *is* supported (so is one on the implementation); ⑥ for `@Async`/executor calls see the thread-boundary note under "Behaviour and limitations".

**Q: How do I turn annotation switching off completely?**
A: Set `spring.data.mongodb.dynamic.aop.enabled=false` (default `true`): the advisor is not registered, so `@DynamicMongo` is never intercepted and dynamic value resolution (`#header…` etc.) does not run. Manual `DynamicMongoDatabaseContextHolder.push()/poll()` slices still work. If it still switches, first make sure the property was actually read (the prefix is `spring.data.mongodb.dynamic.aop.enabled`).

**Q: Can I switch credentials or clusters per request?**
A: A dynamic source *is* a `MongoProperties`, so `uri`, `username`, `password` and `host` can differ per source — each source gets its own connection.

**Q: Which database does `@DynamicMongo("slave")` hit when `databaseName` is omitted?**
A: `instanceId` switches the instance, not the database. Without `databaseName` the name comes from, in order: the enclosing context → that dynamic source's `database` → the default source's database name. Pass `databaseName` to be explicit.

## License

[MIT](LICENSE) · 中文文档: [README.md](README.md)
