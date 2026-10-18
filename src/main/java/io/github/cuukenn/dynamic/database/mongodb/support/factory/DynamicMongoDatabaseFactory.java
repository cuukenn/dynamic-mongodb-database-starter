package io.github.cuukenn.dynamic.database.mongodb.support.factory;

import com.mongodb.client.MongoClient;
import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoProperties;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientFactory;

/**
 * @author changgg
 */
public class DynamicMongoDatabaseFactory extends AbstractDynamicMongoDatabaseFactory {
    public DynamicMongoDatabaseFactory(MongoClient mongoClient, String databaseName, DynamicMongoClientFactory dynamicMongoClientFactory) {
        super(mongoClient, databaseName, dynamicMongoClientFactory);
    }

    /**
     * @param dynamicMongoProperties 动态源配置：注解没写 databaseName 时用它自己的 database 当默认库
     */
    public DynamicMongoDatabaseFactory(MongoClient mongoClient, String databaseName,
                                       DynamicMongoClientFactory dynamicMongoClientFactory,
                                       DynamicMongoProperties dynamicMongoProperties) {
        super(mongoClient, databaseName, dynamicMongoClientFactory, dynamicMongoProperties);
    }
}
