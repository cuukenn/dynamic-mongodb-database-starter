package com.opensource.cuukenn.dynamic.database.mongodb;

import com.opensource.cuukenn.dynamic.database.mongodb.configurate.DynamicMongoAopConfiguration;
import com.opensource.cuukenn.dynamic.database.mongodb.configurate.DynamicMongoClientConfiguration;
import com.opensource.cuukenn.dynamic.database.mongodb.configurate.DynamicMongoClientFactoryConfiguration;
import com.opensource.cuukenn.dynamic.database.mongodb.properties.DynamicMongodbProperties;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author changgg
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DynamicMongodbProperties.class})
@ConditionalOnProperty(prefix = DynamicMongodbProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({DynamicMongoAopConfiguration.class, DynamicMongoClientConfiguration.class, DynamicMongoClientFactoryConfiguration.class})
@AutoConfigureAfter(MongoAutoConfiguration.class)
public class DynamicMongoAutoConfiguration {
}
