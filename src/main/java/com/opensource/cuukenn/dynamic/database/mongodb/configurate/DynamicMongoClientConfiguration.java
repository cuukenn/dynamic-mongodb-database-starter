package com.opensource.cuukenn.dynamic.database.mongodb.configurate;

import com.mongodb.client.MongoClient;
import com.opensource.cuukenn.dynamic.database.mongodb.support.factory.DynamicMongoClientFactory;
import com.opensource.cuukenn.dynamic.database.mongodb.support.factory.DynamicMongoDatabaseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;

/**
 * @author changgg
 */
@Configuration(proxyBeanMethods = false)
public class DynamicMongoClientConfiguration {
    private final Logger logger = LoggerFactory.getLogger(DynamicMongoClientConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(MongoDatabaseFactory.class)
    public MongoDatabaseFactory mongoClientFactory(MongoClient mongoClient, MongoProperties properties,
                                                   DynamicMongoClientFactory dynamicMongoClientFactory) {
        logger.info("register dynamic mongo database factory");
        return new DynamicMongoDatabaseFactory(mongoClient, properties.getMongoClientDatabase(), dynamicMongoClientFactory);
    }
}
