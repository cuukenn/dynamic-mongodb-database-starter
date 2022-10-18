package io.github.cuukenn.dynamic.database.mongodb.support.factory;

import com.mongodb.client.MongoClient;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientFactory;

/**
 * @author changgg
 */
public class DynamicMongoDatabaseFactory extends AbstractDynamicMongoDatabaseFactory {
    public DynamicMongoDatabaseFactory(MongoClient mongoClient, String databaseName, DynamicMongoClientFactory dynamicMongoClientFactory) {
        super(mongoClient, databaseName, dynamicMongoClientFactory);
    }
}
