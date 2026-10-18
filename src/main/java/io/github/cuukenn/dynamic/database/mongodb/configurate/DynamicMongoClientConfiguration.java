package io.github.cuukenn.dynamic.database.mongodb.configurate;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoProperties;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientBuilder;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientFactory;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientPropertiesCustomizer;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientSettingsCustomizer;
import io.github.cuukenn.dynamic.database.mongodb.support.factory.DynamicMongoDatabaseFactory;import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mongo.MongoClientFactory;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.autoconfigure.mongo.MongoPropertiesClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author changgg
 */
@Configuration(proxyBeanMethods = false)
public class DynamicMongoClientConfiguration {
    private final Logger logger = LoggerFactory.getLogger(DynamicMongoClientConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MongoDatabaseFactory mongoClientFactory(MongoClient mongoClient, MongoProperties properties,
                                                   DynamicMongoClientFactory dynamicMongoClientFactory,
                                                   DynamicMongoProperties dynamicMongoProperties) {
        logger.info("register dynamic mongo database factory");
        return new DynamicMongoDatabaseFactory(mongoClient, properties.getMongoClientDatabase(), dynamicMongoClientFactory,
            dynamicMongoProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicMongoClientBuilder dynamicMongoClientBuilder(MongoProperties primaryProperties,
                                                               ObjectProvider<DynamicMongoClientSettingsCustomizer> settingsCustomizers,
                                                               ObjectProvider<DynamicMongoClientPropertiesCustomizer> propertiesCustomizers,
                                                               ObjectProvider<MongoClientSettingsBuilderCustomizer> clientSettingsBuilderCustomizers) {
        logger.info("register dynamic mongo client builder");
        return ((instanceId, properties) -> {
            propertiesCustomizers.orderedStream().forEach(customizers -> customizers.customize(primaryProperties, instanceId, properties));
            List<MongoClientSettingsBuilderCustomizer> customizers = new ArrayList<>();
            //动态源自己的配置(uri/host/凭据)先应用:applyConnectionString 会整体替换集群设置,
            //必须排在应用的通用设置之前,否则会把应用设的集群配置冲掉
            customizers.add(clientSettingsBuilder -> settingsCustomizers.orderedStream().forEach(customizers2 -> customizers2.customize(
                instanceId,
                clientSettingsBuilder,
                properties
            )));
            //应用用 Boot 惯用法声明的全局设置(TLS/连接池/集群)也要作用到动态源;
            //但必须排除 Boot 为「默认源」注册的那个 customizer,否则默认源的 uri 会泄进动态源 -> 连错集群
            clientSettingsBuilderCustomizers.orderedStream()
                .filter(customizer -> !(customizer instanceof MongoPropertiesClientSettingsBuilderCustomizer))
                .forEach(customizers::add);
            return new MongoClientFactory(customizers).createMongoClient(MongoClientSettings.builder().build());
        });
    }

    @Bean("dynamicMongoClientSettingsCustomizer")
    @ConditionalOnMissingBean(name = "dynamicMongoClientSettingsCustomizer")
    public DynamicMongoClientSettingsCustomizer dynamicMongoClientSettingsCustomizer(Environment environment) {
        logger.info("register dynamic mongo client setting customizer");
        return (instanceId, dynamic, properties) -> new MongoPropertiesClientSettingsBuilderCustomizer(properties, environment).customize(dynamic);
    }
}
