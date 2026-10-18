package io.github.cuukenn.dynamic.database.mongodb.support;

import com.mongodb.client.MongoClient;

import java.util.Collections;
import java.util.Set;

/**
 * @author changgg
 */
@FunctionalInterface
public interface DynamicMongoClientFactory {
    /**
     * 根据键获取指定mongo客户端
     *
     * @param key 键
     * @return mongo客户端
     */
    MongoClient getDynamicMongoClient(String key);

    /**
     * 已配置的动态实例 id 集合，仅用于在找不到实例时报出「你配了哪些」。
     * 给了默认实现：只实现 {@link #getDynamicMongoClient(String)} 的既有实现不需要改动。
     *
     * @return 已配置的实例 id
     */
    default Set<String> getInstanceIds() {
        return Collections.emptySet();
    }
}
