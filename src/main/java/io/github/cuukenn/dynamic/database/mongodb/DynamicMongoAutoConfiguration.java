package io.github.cuukenn.dynamic.database.mongodb;

import io.github.cuukenn.dynamic.database.mongodb.configurate.DynamicMongoAopConfiguration;
import io.github.cuukenn.dynamic.database.mongodb.configurate.DynamicMongoClientConfiguration;
import io.github.cuukenn.dynamic.database.mongodb.configurate.DynamicMongoClientFactoryConfiguration;
import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoProperties;
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
@EnableConfigurationProperties({DynamicMongoProperties.class})
@ConditionalOnProperty(prefix = DynamicMongoProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({DynamicMongoAopConfiguration.class, DynamicMongoClientConfiguration.class, DynamicMongoClientFactoryConfiguration.class})
@AutoConfigureAfter(MongoAutoConfiguration.class)
public class DynamicMongoAutoConfiguration {
}
