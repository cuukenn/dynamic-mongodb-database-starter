package io.github.cuukenn.dynamic.database.mongodb.support.factory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoProperties;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientFactory;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DefaultDynamicMongoContext;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DynamicMongoDatabaseContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 动态 DatabaseFactory 的核心行为：按上下文里的 instanceId 换客户端、按 database 换库。
 * 之前没有任何测试覆盖它——而它正是「切换到底有没有生效」的唯一落点。
 */
class DynamicMongoDatabaseFactoryTest {

    private final MongoClient defaultClient = mock(MongoClient.class);
    private final MongoClient slaveClient = mock(MongoClient.class);
    private final DynamicMongoClientFactory clientFactory = id -> "slave".equals(id) ? slaveClient : null;

    private DynamicMongoDatabaseFactory databaseFactory() {
        return new DynamicMongoDatabaseFactory(defaultClient, "default_db", clientFactory);
    }

    /**
     * 两个动态源 + 各自可识别的客户端，用于「继承外层上下文」场景。
     */
    private DynamicMongoDatabaseFactory databaseFactoryWith(String firstId, MongoClient firstClient,
                                                             String secondId, MongoClient secondClient) {
        DynamicMongoClientFactory factory = id -> {
            if (firstId.equals(id)) {
                return firstClient;
            }
            return secondId.equals(id) ? secondClient : null;
        };
        return new DynamicMongoDatabaseFactory(defaultClient, "default_db", factory);
    }

    /**
     * slave 源 + 它在 spring.data.mongodb.dynamic.config.slave.database 里配的库名。
     */
    private DynamicMongoDatabaseFactory databaseFactoryWithSourceDatabase(String instanceId, String database) {
        Map<String, MongoProperties> config = new LinkedHashMap<>();
        MongoProperties properties = new MongoProperties();
        properties.setUri("mongodb://slavehost:27017/uri_db");
        properties.setDatabase(database);
        config.put(instanceId, properties);
        DynamicMongoProperties dynamicMongoProperties = new DynamicMongoProperties();
        dynamicMongoProperties.setConfig(config);

        DynamicMongoClientFactory factory = id -> instanceId.equals(id) ? slaveClient : null;
        return new DynamicMongoDatabaseFactory(defaultClient, "default_db", factory, dynamicMongoProperties);
    }

    @AfterEach
    void clearContext() {
        DynamicMongoDatabaseContextHolder.clear();
    }

    @Test
    void usesDefaultClientWithoutContext() {
        assertSame(defaultClient, databaseFactory().getMongoClient());
    }

    @Test
    void usesDefaultClientWhenInstanceIdIsBlank() {
        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("", ""));
        assertSame(defaultClient, databaseFactory().getMongoClient());
    }

    @Test
    void usesDynamicClientFromContext() {
        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("slave", ""));
        assertSame(slaveClient, databaseFactory().getMongoClient());
    }

    @Test
    void unknownInstanceIdFailsWithActionableMessage() {
        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("nope", ""));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> databaseFactory().getMongoClient());

        assertTrue(error.getMessage().contains("nope"), "报错必须点名找不到的 instanceId，实际: " + error.getMessage());
        assertTrue(error.getMessage().contains("spring.data.mongodb.dynamic.config"),
            "报错必须告诉使用者去哪儿配，实际: " + error.getMessage());
    }

    @Test
    void switchesDatabaseFromContext() {
        MongoDatabase otherDatabase = mock(MongoDatabase.class);
        when(defaultClient.getDatabase("other_db")).thenReturn(otherDatabase);

        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("", "other_db"));

        assertSame(otherDatabase, databaseFactory().getMongoDatabase());
    }

    @Test
    void keepsDefaultDatabaseWithoutContext() {
        MongoDatabase defaultDatabase = mock(MongoDatabase.class);
        when(defaultClient.getDatabase("default_db")).thenReturn(defaultDatabase);

        assertSame(defaultDatabase, databaseFactory().getMongoDatabase());
    }

    /**
     * instanceId 与 database 同时给出时必须两者都生效（换客户端 + 换库），且新库要从**动态客户端**上取。
     */
    @Test
    void switchesClientAndDatabaseWhenBothSet() {
        MongoDatabase slaveDatabase = mock(MongoDatabase.class);
        when(slaveClient.getDatabase("slave_db")).thenReturn(slaveDatabase);

        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("slave", "slave_db"));

        DynamicMongoDatabaseFactory factory = databaseFactory();
        assertSame(slaveClient, factory.getMongoClient());
        assertSame(slaveDatabase, factory.getMongoDatabase(), "切库必须发生在动态客户端上");
    }

    /**
     * 注解里没写的字段表示「继承外层上下文」，不是「回落默认源」：
     * 外层切到 slave，内层方法只声明了库名 —— 客户端必须还是 slave。
     */
    @Test
    void inheritsOuterInstanceIdWhenInnerContextOnlySwitchesDatabase() {
        MongoDatabase archiveDatabase = mock(MongoDatabase.class);
        when(slaveClient.getDatabase("archive_db")).thenReturn(archiveDatabase);
        DynamicMongoDatabaseFactory factory = databaseFactoryWith("slave", slaveClient, "report", mock(MongoClient.class));

        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("slave", ""));
        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("", "archive_db"));

        assertSame(slaveClient, factory.getMongoClient(), "内层 instanceId 为空表示继承外层的 slave");
        assertSame(archiveDatabase, factory.getMongoDatabase(), "切库要发生在继承来的客户端上");
    }

    /**
     * 反过来同理：内层只换实例、没写库名时，库名继承外层（而不是悄悄回到默认源的库）。
     */
    @Test
    void inheritsOuterDatabaseWhenInnerContextOnlySwitchesInstance() {
        MongoClient reportClient = mock(MongoClient.class);
        MongoDatabase inheritedDatabase = mock(MongoDatabase.class);
        when(reportClient.getDatabase("slave_db")).thenReturn(inheritedDatabase);
        DynamicMongoDatabaseFactory factory = databaseFactoryWith("slave", slaveClient, "report", reportClient);

        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("slave", "slave_db"));
        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("report", ""));

        assertSame(reportClient, factory.getMongoClient());
        assertSame(inheritedDatabase, factory.getMongoDatabase(), "库名未写时继承外层的库名");
    }

    /**
     * 注解没给库名时，用该动态源自己配置里的 database —— 配置项写了就该生效。
     */
    @Test
    void usesSourceOwnDatabaseWhenAnnotationHasNoDatabaseName() {
        MongoDatabase sourceDatabase = mock(MongoDatabase.class);
        when(slaveClient.getDatabase("slave_db")).thenReturn(sourceDatabase);
        DynamicMongoDatabaseFactory factory = databaseFactoryWithSourceDatabase("slave", "slave_db");

        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("slave", ""));

        assertSame(sourceDatabase, factory.getMongoDatabase(), "应该用 slave 源自己配的 database");
    }

    /**
     * 注解的 databaseName 优先级最高。
     */
    @Test
    void annotationDatabaseWinsOverSourceOwnDatabase() {
        MongoDatabase annotationDatabase = mock(MongoDatabase.class);
        when(slaveClient.getDatabase("other_db")).thenReturn(annotationDatabase);
        DynamicMongoDatabaseFactory factory = databaseFactoryWithSourceDatabase("slave", "slave_db");

        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("slave", "other_db"));

        assertSame(annotationDatabase, factory.getMongoDatabase());
    }

    /**
     * 该源没配 database 时，库名回落「默认源的库名」，但客户端仍然是该动态源
     * （uri 里的库名不算，免得把数据搬到 uri 的库上）。
     */
    @Test
    void fallsBackToDefaultDatabaseNameWhenSourceHasNoDatabase() {
        MongoDatabase defaultNamedDatabase = mock(MongoDatabase.class);
        when(slaveClient.getDatabase("default_db")).thenReturn(defaultNamedDatabase);
        DynamicMongoDatabaseFactory factory = databaseFactoryWithSourceDatabase("slave", null);

        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("slave", ""));

        assertSame(slaveClient, factory.getMongoClient());
        assertSame(defaultNamedDatabase, factory.getMongoDatabase(), "库名用默认源的库名，但作用在 slave 客户端上");
    }

    /**
     * 使用者最常见的困惑是「instanceId 到底该配在哪儿、我配了哪些」。报错直接列出来。
     */
    @Test
    void unknownInstanceIdListsConfiguredInstances() throws Exception {
        DynamicMongoProperties properties = new DynamicMongoProperties();
        Map<String, MongoProperties> config = new LinkedHashMap<>();
        config.put("slave", new MongoProperties());
        config.put("report", new MongoProperties());
        properties.setConfig(config);
        SimpleDynamicMongoClientFactory clientFactory =
            new SimpleDynamicMongoClientFactory(properties, (instanceId, mongoProperties) -> mock(MongoClient.class));
        clientFactory.afterPropertiesSet();

        try {
            DynamicMongoDatabaseFactory databaseFactory =
                new DynamicMongoDatabaseFactory(defaultClient, "default_db", clientFactory);
            DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("nope", ""));

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, databaseFactory::getMongoClient);

            assertTrue(error.getMessage().contains("slave"), "应列出已配置的实例，实际: " + error.getMessage());
            assertTrue(error.getMessage().contains("report"), "应列出已配置的实例，实际: " + error.getMessage());
        } finally {
            clientFactory.destroy();
        }
    }

    @Test
    void unknownInstanceIdSaysConfigIsEmptyWhenNothingConfigured() {
        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("nope", ""));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> databaseFactory().getMongoClient());

        assertTrue(error.getMessage().contains("empty"), "没有任何动态源时要明说，实际: " + error.getMessage());
    }
}
