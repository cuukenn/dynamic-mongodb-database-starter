package io.github.cuukenn.dynamic.database.mongodb.support.factory;

import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoProperties;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientBuilder;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 回归测试：未配置任何动态数据源时，容器关闭不应因 destroy() 遍历 null 而 NPE。
 */
class SimpleDynamicMongoClientFactoryTest {

    @Test
    void destroyWithoutDynamicConfigDoesNotThrow() {
        DynamicMongoProperties properties = new DynamicMongoProperties(); // config == null（用户只用默认源）
        DynamicMongoClientBuilder neverCalled = (instanceId, props) -> null;
        SimpleDynamicMongoClientFactory factory = new SimpleDynamicMongoClientFactory(properties, neverCalled);

        assertDoesNotThrow(() -> {
            factory.afterPropertiesSet();
            factory.destroy();
        }, "无动态配置时 destroy() 不应抛异常");
    }

    /**
     * 配置里有一个源写错（比如只写了 database）时，构建会抛异常；此时前面已经建好的客户端必须有人关掉，
     * 否则它们的连接池要等进程退出才释放。bean 初始化失败时 Spring 不会调用 destroy()，所以清理只能自己做。
     */
    @Test
    void closesAlreadyBuiltClientsWhenOneSourceFails() {
        DynamicMongoProperties properties = new DynamicMongoProperties();
        Map<String, MongoProperties> config = new LinkedHashMap<>();
        config.put("first", new MongoProperties());
        config.put("second", new MongoProperties());
        properties.setConfig(config);

        MongoClient firstClient = mock(MongoClient.class);
        DynamicMongoClientBuilder builder = (instanceId, props) -> {
            if ("first".equals(instanceId)) {
                return firstClient;
            }
            throw new IllegalStateException("invalid configuration for " + instanceId);
        };
        SimpleDynamicMongoClientFactory factory = new SimpleDynamicMongoClientFactory(properties, builder);

        assertThatThrownBy(factory::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("second");

        verify(firstClient).close();
    }

    /**
     * 每个配置项预建一个客户端、按 instanceId 取用、销毁时逐个关闭；未知 instanceId 返回 null
     * （具体报错由 AbstractDynamicMongoDatabaseFactory 负责，那里才知道自己配了哪些实例）。
     */
    @Test
    void buildsOneClientPerSourceAndClosesThemOnDestroy() throws Exception {
        DynamicMongoProperties properties = new DynamicMongoProperties();
        Map<String, MongoProperties> config = new LinkedHashMap<>();
        config.put("slave", new MongoProperties());
        config.put("report", new MongoProperties());
        properties.setConfig(config);

        Map<String, MongoClient> built = new LinkedHashMap<>();
        DynamicMongoClientBuilder builder = (instanceId, props) -> {
            MongoClient client = mock(MongoClient.class);
            built.put(instanceId, client);
            return client;
        };
        SimpleDynamicMongoClientFactory factory = new SimpleDynamicMongoClientFactory(properties, builder);

        factory.afterPropertiesSet();

        assertThat(built.keySet()).as("每个配置项都要建客户端").containsExactly("slave", "report");
        assertThat(factory.getDynamicMongoClient("slave")).isSameAs(built.get("slave"));
        assertThat(factory.getDynamicMongoClient("report")).isSameAs(built.get("report"));
        assertThat(factory.getDynamicMongoClient("nope")).isNull();
        assertThat(factory.getInstanceIds()).containsExactlyInAnyOrder("slave", "report");

        factory.destroy();

        for (MongoClient client : built.values()) {
            verify(client).close();
        }
    }

    /**
     * 没配任何动态源时，取值返回 null、实例列表为空 —— 不能 NPE，也不能报「已配置实例」。
     */
    @Test
    void returnsNullAndNoInstanceIdsWithoutConfig() throws Exception {
        DynamicMongoProperties properties = new DynamicMongoProperties();
        SimpleDynamicMongoClientFactory factory =
            new SimpleDynamicMongoClientFactory(properties, (instanceId, props) -> null);

        factory.afterPropertiesSet();

        assertThat(factory.getDynamicMongoClient("slave")).isNull();
        assertThat(factory.getInstanceIds()).isEmpty();
    }
}
