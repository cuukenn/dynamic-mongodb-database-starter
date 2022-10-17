package io.github.cuukenn.dynamic.database.mongodb.support.factory;

import com.mongodb.client.MongoClient;
import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongodbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * @author changgg
 */
public class SimpleDynamicMongoClientFactory implements DynamicMongoClientFactory, InitializingBean, DisposableBean {
    private final Logger logger = LoggerFactory.getLogger(SimpleDynamicMongoClientFactory.class);
    private final DynamicMongodbProperties properties;
    private final DynamicMongoClientBuilder mongoClientBuilder;
    private Map<String, MongoClient> dynamicMongoClients;

    public SimpleDynamicMongoClientFactory(DynamicMongodbProperties properties, DynamicMongoClientBuilder mongoClientBuilder) {
        this.properties = properties;
        this.mongoClientBuilder = mongoClientBuilder;
    }

    @Override
    public MongoClient getDynamicMongoClient(String key) {
        return dynamicMongoClients != null ? dynamicMongoClients.get(key) : null;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, MongoProperties> config;
        if ((config = properties.getConfig()) == null || config.isEmpty()) {
            logger.warn("no dynamic mongodb config!!!");
            return;
        }
        dynamicMongoClients = new HashMap<>(config.size());
        for (Map.Entry<String, MongoProperties> entry : config.entrySet()) {
            logger.info("init dynamic mongo database for {}", entry.getKey());
            dynamicMongoClients.put(entry.getKey(), mongoClientBuilder.build(entry.getValue()));
        }
    }

    @Override
    public void destroy() throws Exception {
        for (Map.Entry<String, MongoClient> entry : dynamicMongoClients.entrySet()) {
            logger.info("closing dynamic mongo client for name:{} ...", entry.getKey());
            try {
                MongoClient client;
                if ((client = entry.getValue()) != null) {
                    client.close();
                }
            } catch (RuntimeException e) {
                logger.error("close dynamic mongo client failed for name:{},msg:{}", entry.getKey(), e.getMessage());
            }
        }
    }
}
