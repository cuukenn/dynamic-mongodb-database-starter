# 简介

dynamic-mongodb-database-starter是一个基于springboot的快速集成Mongodb多数据源的启动器。

# 特性

- 支持 **切换数据源实例**。
- 支持 **切换数据库**。
- 支持 **SpEL表达式获取instanceId和database**。
- 支持 **session获取instanceId和database**。
- 支持 **request header获取instanceId和database**。

# 使用方法

1. 引入dynamic-mongodb-database-starter。

```xml
<!-- 具体发布版本可通过以下地址查询 -->
<!-- https://mvnrepository.com/artifact/io.github.cuukenn/dynamic-mongodb-database-starter -->
<dependency>
  <groupId>io.github.cuukenn</groupId>
  <artifactId>dynamic-mongodb-database-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

2. 配置数据源。

```yaml
spring:
  data:
    mongodb:
      host: 127.0.0.1
      xxx: xxx
      dynamic:
        config:
          test-1:
            host: 127.0.0.1
            xxx: xxx
          test-2:
            host: 127.0.0.1
            xxx: xxx
          #......省略
          #以上配置mongodb下的为默认源
          #dynamic.config中的为动态源
          #相关配置与原mongo配置一致
```

3. 使用  **@DynamicMongo**  切换数据源。

**@DynamicMongo** 可以注解在方法上或类上，**同时存在就近原则 方法上注解 优先于 类上注解**。

| 注解                                                         | 结果                                     |
| :----------------------------------------------------------- | :--------------------------------------- |
| 没有@DS                                                      | 默认数据源                               |
| @DynamicMongo("instanceId")                                  | instanceId为指定库key                    |
| @DynamicMongo(databaseName="databaseName")                   | databaseName为库名                       |
| @DynamicMongo(instanceId="instanceId",databaseName="databaseName") | instanceId为指定库key,databaseName为库名 |

```java

@Service
@DynamicMongo("slave")
public class UserServiceImpl implements IUserService {

  @Autowired
  private MongoTemplate mongoTemplate;

  public List selectAll() {
    //默认实例，默认数据库
    return mongoTemplate.xxx();
  }

  @Override
  @DynamicMongo("slave_1")
  public List selectByCondition() {
    //slave_1实例，默认数据库
    return mongoTemplate.xxx();
  }

  @Override
  @DynamicMongo(instanceId = "slave_1", databaseName = "databaseName_1")
  public List selectByCondition() {
    //slave_1实例，databaseName_1数据库
    return mongoTemplate.xxx();
  }
}
```

4. 代码示例

- [码云 Demo](https://gitee.com/cuukenn/open-source-study/tree/master/sample/dynamic-mongodb-demo)
- [Github Demo](https://github.com/cuukenn/open-source-study/tree/master/sample/dynamic-mongodb-demo)