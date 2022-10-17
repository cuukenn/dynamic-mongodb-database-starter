package io.github.cuukenn.dynamic.database.mongodb.configurate;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import io.github.cuukenn.dynamic.database.mongodb.support.factory.DynamicMongoClientBuilder;
import io.github.cuukenn.dynamic.database.mongodb.support.factory.DynamicMongoClientFactory;
import io.github.cuukenn.dynamic.database.mongodb.support.factory.DynamicMongoDatabaseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mongo.MongoClientFactory;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.autoconfigure.mongo.MongoPropertiesClientSettingsBuilderCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @Bean
    @ConditionalOnMissingBean(DynamicMongoClientBuilder.class)
    public DynamicMongoClientBuilder dynamicMongoClientBuilder(ApplicationContext applicationContext) {
        logger.info("register dynamic mongo client builder");
        return properties -> {
            Environment environment = applicationContext.getBean(Environment.class);
            ObjectProvider<MongoClientSettingsBuilderCustomizer> builderCustomizers = applicationContext.getBeanProvider(MongoClientSettingsBuilderCustomizer.class);
            List<MongoClientSettingsBuilderCustomizer> builderCustomizersList = new ArrayList<>();
            builderCustomizersList.add(new MongoPropertiesClientSettingsBuilderCustomizer(properties, environment));
            builderCustomizersList.addAll(builderCustomizers.orderedStream()
                .filter(customizer -> !(customizer instanceof MongoPropertiesClientSettingsBuilderCustomizer))
                .collect(Collectors.toList()));
            return new MongoClientFactory(builderCustomizersList).createMongoClient(MongoClientSettings.builder().build());
        };
    }
}
