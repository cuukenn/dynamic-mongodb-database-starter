package io.github.cuukenn.dynamic.database.mongodb.support.factory;

import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongodbProperties;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.mongo.MongoClientFactory;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.autoconfigure.mongo.MongoPropertiesClientSettingsBuilderCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author changgg
 */
public class SimpleDynamicMongoClientFactory implements DynamicMongoClientFactory, ApplicationContextAware, InitializingBean, DisposableBean {
    private final Logger logger = LoggerFactory.getLogger(SimpleDynamicMongoClientFactory.class);
    private final DynamicMongodbProperties properties;
    private Map<String, MongoClient> dynamicMongoClients;
    private ApplicationContext applicationContext;

    public SimpleDynamicMongoClientFactory(DynamicMongodbProperties properties) {
        this.properties = properties;
    }

    @Override
    public MongoClient getDynamicMongoClient(String key) {
        return dynamicMongoClients != null ? dynamicMongoClients.get(key) : null;
    }

    /**
     * 创建client实例
     *
     * @param properties 参数
     * @return mongoClient
     */
    protected MongoClient buildMongoClient(MongoProperties properties) {
        Environment environment = applicationContext.getBean(Environment.class);
        ObjectProvider<MongoClientSettingsBuilderCustomizer> builderCustomizers = applicationContext.getBeanProvider(MongoClientSettingsBuilderCustomizer.class);
        List<MongoClientSettingsBuilderCustomizer> builderCustomizersList = new ArrayList<>();
        builderCustomizersList.add(new MongoPropertiesClientSettingsBuilderCustomizer(properties, environment));
        builderCustomizersList.addAll(builderCustomizers.orderedStream()
            .filter(customizer -> !(customizer instanceof MongoPropertiesClientSettingsBuilderCustomizer))
            .collect(Collectors.toList()));
        return new MongoClientFactory(builderCustomizersList).createMongoClient(MongoClientSettings.builder().build());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
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
            dynamicMongoClients.put(entry.getKey(), buildMongoClient(entry.getValue()));
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
