package io.github.cuukenn.dynamic.database.mongodb;

import io.github.cuukenn.dynamic.database.mongodb.configurate.DynamicMongoAopConfiguration;
import io.github.cuukenn.dynamic.database.mongodb.configurate.DynamicMongoClientConfiguration;
import io.github.cuukenn.dynamic.database.mongodb.configurate.DynamicMongoClientFactoryConfiguration;
import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoProperties;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 自动配置入口。
 * <p>
 * 必须显式声明装配顺序：本类提供的 MongoDatabaseFactory 与 Boot 的
 * {@link MongoDataAutoConfiguration} 抢同一个 Bean（双方都是
 * {@code @ConditionalOnMissingBean(MongoDatabaseFactory.class)}），而 Boot 的排序在 order 相同时
 * 只按类名字母序兜底——依赖它等于依赖隐式约定，名字/包名一变就会静默失效（不报错，只是动态切换
 * 整条链路失效）。因此这里显式声明 @AutoConfigureBefore。
 *
 * @author changgg
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DynamicMongoProperties.class})
@ConditionalOnProperty(prefix = DynamicMongoProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({DynamicMongoAopConfiguration.class, DynamicMongoClientConfiguration.class, DynamicMongoClientFactoryConfiguration.class})
@AutoConfigureAfter(MongoAutoConfiguration.class)
@AutoConfigureBefore(MongoDataAutoConfiguration.class)
public class DynamicMongoAutoConfiguration {
}
