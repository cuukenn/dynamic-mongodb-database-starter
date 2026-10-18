package io.github.cuukenn.dynamic.database.mongodb.configurate;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.internal.MongoClientImpl;
import io.github.cuukenn.dynamic.database.mongodb.DynamicMongoAutoConfiguration;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 动态源客户端与「应用的全局 Mongo 设置」的关系：
 * <ul>
 *     <li>应用用 Boot 惯用法声明的 {@link MongoClientSettingsBuilderCustomizer}（TLS、连接池、集群设置）必须作用到动态源，
 *     否则动态源会静默缺少 TLS 等设置；</li>
 *     <li>但 Boot 为**默认源**注册的那个 customizer 必须排除，否则默认源的 uri 会泄进动态源 —— 连错集群。</li>
 * </ul>
 * 断言读的是驱动内部实现类暴露的 settings（版本由 parent 固定，仅测试使用）。
 */
class DynamicMongoClientBuilderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DynamicMongoAutoConfiguration.class, MongoAutoConfiguration.class))
        .withPropertyValues("spring.data.mongodb.uri=mongodb://localhost:27017/default_db",
            "spring.data.mongodb.dynamic.config.slave.uri=mongodb://slavehost:27017/slave_db")
        .withUserConfiguration(ApplicationCustomizerConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    static class ApplicationCustomizerConfiguration {
        @Bean
        MongoClientSettingsBuilderCustomizer replicaSetCustomizer() {
            return builder -> builder.applyToClusterSettings(settings -> settings.requiredReplicaSetName("rs0"));
        }
    }

    private static MongoClientSettings settingsOf(MongoClient client) {
        return ((MongoClientImpl) client).getSettings();
    }

    private static MongoProperties slaveProperties() {
        MongoProperties properties = new MongoProperties();
        properties.setUri("mongodb://slavehost:27017/slave_db");
        return properties;
    }

    @Test
    void appliesApplicationCustomizersToDynamicClients() {
        contextRunner.run(context -> {
            MongoClient client = context.getBean(DynamicMongoClientBuilder.class).build("slave", slaveProperties());
            try {
                assertThat(settingsOf(client).getClusterSettings().getRequiredReplicaSetName())
                    .as("应用声明的 MongoClientSettingsBuilderCustomizer 必须作用到动态源")
                    .isEqualTo("rs0");
            } finally {
                client.close();
            }
        });
    }

    @Test
    void doesNotLeakDefaultSourceSettingsIntoDynamicClients() {
        contextRunner.run(context -> {
            MongoClient client = context.getBean(DynamicMongoClientBuilder.class).build("slave", slaveProperties());
            try {
                assertThat(settingsOf(client).getClusterSettings().getHosts())
                    .as("动态源只能用自己 uri 里的地址，不能继承默认源的 localhost")
                    .containsExactly(new ServerAddress("slavehost", 27017));
            } finally {
                client.close();
            }
        });
    }
}
