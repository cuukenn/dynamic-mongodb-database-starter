# Describe

dynamic-mongodb-database-starter is a dynamic mongo database components for SpringBoot。

# Feature

- Support **dynamic mongo instance**。
- Support **dynamic mongo database**。
- Support **read instanceId and database by SpEL**。
- Support **read instanceId and database from Session**。
- Support **read instanceId and database from request Header**。

# How to use it

1. Install dynamic-mongodb-database-starter。

```xml
<!-- you can find the version in the follow website -->
<!-- https://mvnrepository.com/artifact/io.github.cuukenn/dynamic-mongodb-database-starter -->
<dependency>
  <groupId>io.github.cuukenn</groupId>
  <artifactId>dynamic-mongodb-database-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

2. Configurate

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
```

3. use  **@DynamicMongo** to switch mongo database。

**@DynamicMongo** use in class or method，**method first,class second**。

| annotation                                                   | result                                        |
| :----------------------------------------------------------- | :-------------------------------------------- |
| no @DynamicMongo in class and method                         | default                                       |
| @DynamicMongo("instanceId")                                  | mongodb instance of instanceId                |
| @DynamicMongo(databaseName="databaseName")                   | mongodb dayabase of databaseName              |
| @DynamicMongo(instanceId="instanceId",databaseName="databaseName") | mongodb instance of instanceId & databaseName |

```java

@Service
@DynamicMongo("slave")
public class UserServiceImpl implements IUserService {

  @Autowired
  private MongoTemplate mongoTemplate;

  public List selectAll() {
    //default instance,default databaseName
    return mongoTemplate.xxx();
  }

  @Override
  @DynamicMongo("slave_1")
  public List selectByCondition() {
    //slave_1 instance，default databaseName
    return mongoTemplate.xxx();
  }

  @Override
  @DynamicMongo(instanceId = "slave_1", databaseName = "databaseName_1")
  public List selectByCondition() {
    //slave_1 instance，databaseName_1 databaseName
    return mongoTemplate.xxx();
  }
}
```

4. Code Example

- [Gitee Demo](https://gitee.com/cuukenn/open-source-study/tree/master/sample/dynamic-mongodb-demo)
- [Github Demo](https://github.com/cuukenn/open-source-study/tree/master/sample/dynamic-mongodb-demo)