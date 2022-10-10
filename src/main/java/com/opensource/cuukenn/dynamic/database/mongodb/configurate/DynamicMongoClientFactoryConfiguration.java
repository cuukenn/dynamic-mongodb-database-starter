package com.opensource.cuukenn.dynamic.database.mongodb.configurate;

import com.opensource.cuukenn.dynamic.database.mongodb.properties.DynamicMongodbProperties;
import com.opensource.cuukenn.dynamic.database.mongodb.support.factory.DynamicMongoClientFactory;
import com.opensource.cuukenn.dynamic.database.mongodb.support.factory.SimpleDynamicMongoClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author changgg
 */
@Configuration(proxyBeanMethods = false)
public class DynamicMongoClientFactoryConfiguration {
    private final Logger logger = LoggerFactory.getLogger(DynamicMongoClientFactoryConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(DynamicMongoClientFactory.class)
    public DynamicMongoClientFactory dynamicMongoClientHolder(DynamicMongodbProperties properties) {
        logger.info("register simple dynamic mongo client factory");
        return new SimpleDynamicMongoClientFactory(properties);
    }
}
