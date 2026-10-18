package io.github.cuukenn.dynamic.database.mongodb.support.factory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoProperties;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientFactory;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DynamicMongoDatabaseContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * @author changgg
 */
public abstract class AbstractDynamicMongoDatabaseFactory extends SimpleMongoClientDatabaseFactory {
    private final Logger logger = LoggerFactory.getLogger(AbstractDynamicMongoDatabaseFactory.class);
    protected final DynamicMongoClientFactory dynamicMongoClientFactory;
    private final DynamicMongoProperties dynamicMongoProperties;

    protected AbstractDynamicMongoDatabaseFactory(MongoClient mongoClient, String databaseName,
                                                  DynamicMongoClientFactory dynamicMongoClientFactory) {
        this(mongoClient, databaseName, dynamicMongoClientFactory, null);
    }

    protected AbstractDynamicMongoDatabaseFactory(MongoClient mongoClient, String databaseName,
                                                  DynamicMongoClientFactory dynamicMongoClientFactory,
                                                  DynamicMongoProperties dynamicMongoProperties) {
        super(mongoClient, databaseName);
        this.dynamicMongoClientFactory = dynamicMongoClientFactory;
        this.dynamicMongoProperties = dynamicMongoProperties;
    }

    /**
     * instanceId 取「栈里最近的非空值」而不是栈顶：注解没写的字段表示继承外层上下文。
     */
    @Override
    protected MongoClient getMongoClient() {
        final String instanceId = DynamicMongoDatabaseContextHolder.peekInstanceId();
        if (StringUtils.hasText(instanceId)) {
            final MongoClient client = this.dynamicMongoClientFactory.getDynamicMongoClient(instanceId);
            Assert.notNull(client, () -> noDynamicClientMessage(instanceId));
            return client;
        }
        return super.getMongoClient();
    }

    /**
     * 找不到实例时的报错必须能自解释：点名 instanceId、指出配置在哪、并列出已配了哪些
     * （否则使用者只能靠读源码才知道 instanceId 是从 spring.data.mongodb.dynamic.config 里取的）。
     */
    private String noDynamicClientMessage(String instanceId) {
        final Set<String> instances = this.dynamicMongoClientFactory.getInstanceIds();
        final String detail = instances.isEmpty()
            ? DynamicMongoProperties.PREFIX + ".config is empty, no dynamic instance is configured"
            : "configured instances: " + instances;
        return "no dynamic mongo database instance found for instanceId '" + instanceId + "', configure it under "
            + DynamicMongoProperties.PREFIX + ".config." + instanceId + "; " + detail;
    }

    /**
     * 库名优先级：注解的 databaseName → 外层上下文的库名（继承）→ 该动态源自己配的 database → 默认源的库名。
     */
    @Override
    public MongoDatabase getMongoDatabase() throws DataAccessException {
        final String database = DynamicMongoDatabaseContextHolder.peekDatabase();
        if (StringUtils.hasText(database)) {
            return getMongoDatabase(database);
        }
        final String instanceDatabase = instanceDatabase(DynamicMongoDatabaseContextHolder.peekInstanceId());
        if (StringUtils.hasText(instanceDatabase)) {
            return getMongoDatabase(instanceDatabase);
        }
        return super.getMongoDatabase();
    }

    /**
     * 动态源配置里显式写的 database（uri 里的库名不算，避免 uri 写了个跟默认源同名/带 test 的库时把数据搬走）。
     */
    private String instanceDatabase(String instanceId) {
        if (!StringUtils.hasText(instanceId) || this.dynamicMongoProperties == null
            || this.dynamicMongoProperties.getConfig() == null) {
            return null;
        }
        final MongoProperties properties = this.dynamicMongoProperties.getConfig().get(instanceId);
        return properties == null ? null : properties.getDatabase();
    }

    /**
     * 栈顶上下文（供子类/调试使用）。路由判断请用 {@link DynamicMongoDatabaseContextHolder#peekInstanceId()} /
     * {@link DynamicMongoDatabaseContextHolder#peekDatabase()}——它们会跳过「没写」的字段。
     */
    protected DynamicMongoContext getContext() {
        final DynamicMongoContext context = DynamicMongoDatabaseContextHolder.peek();
        if (logger.isDebugEnabled()) {
            logger.debug("excepted dynamic mongodb instance is [{}],use default mongo client:[{}]", context, context != null && !StringUtils.hasText(context.getInstanceId()));
        }
        return context;
    }
}
