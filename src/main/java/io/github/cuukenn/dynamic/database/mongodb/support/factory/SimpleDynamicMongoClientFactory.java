package io.github.cuukenn.dynamic.database.mongodb.support.factory;

import com.mongodb.client.MongoClient;
import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoProperties;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientBuilder;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author changgg
 */
public class SimpleDynamicMongoClientFactory implements DynamicMongoClientFactory, InitializingBean, DisposableBean {
    private final Logger logger = LoggerFactory.getLogger(SimpleDynamicMongoClientFactory.class);
    private final DynamicMongoProperties properties;
    private final DynamicMongoClientBuilder mongoClientBuilder;
    private Map<String, MongoClient> dynamicMongoClients;

    public SimpleDynamicMongoClientFactory(DynamicMongoProperties properties, DynamicMongoClientBuilder mongoClientBuilder) {
        this.properties = properties;
        this.mongoClientBuilder = mongoClientBuilder;
    }

    @Override
    public MongoClient getDynamicMongoClient(String key) {
        return dynamicMongoClients != null ? dynamicMongoClients.get(key) : null;
    }

    @Override
    public Set<String> getInstanceIds() {
        return dynamicMongoClients == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(dynamicMongoClients.keySet()));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, MongoProperties> config;
        if ((config = properties.getConfig()) == null || config.isEmpty()) {
            logger.warn("no dynamic mongodb config!!!");
            return;
        }
        final Map<String, MongoClient> clients = new HashMap<>(config.size());
        try {
            for (Map.Entry<String, MongoProperties> entry : config.entrySet()) {
                logger.info("init dynamic mongo database for {}", entry.getKey());
                clients.put(entry.getKey(), mongoClientBuilder.build(entry.getKey(), entry.getValue()));
            }
        } catch (Exception e) {
            // 初始化失败时 bean 不会注册成功，Spring 也就不会调用 destroy()，已经建好的客户端必须在这里关掉
            logger.error("init dynamic mongo database failed, closing already created clients", e);
            closeClients(clients);
            throw e;
        }
        this.dynamicMongoClients = clients;
    }

    @Override
    public void destroy() throws Exception {
        if (dynamicMongoClients == null) {
            return;
        }
        closeClients(dynamicMongoClients);
    }

    private void closeClients(Map<String, MongoClient> clients) {
        for (Map.Entry<String, MongoClient> entry : clients.entrySet()) {
            logger.info("closing dynamic mongo client for name:{} ...", entry.getKey());
            try {
                MongoClient client;
                if ((client = entry.getValue()) != null) {
                    client.close();
                }
            } catch (RuntimeException e) {
                logger.error("close dynamic mongo client failed for name:{}, msg:{}", entry.getKey(), e.getMessage(), e);
            }
        }
    }
}
