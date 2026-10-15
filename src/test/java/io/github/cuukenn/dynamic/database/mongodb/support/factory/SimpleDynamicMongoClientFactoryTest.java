package io.github.cuukenn.dynamic.database.mongodb.support.factory;

import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoProperties;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoClientBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
}
