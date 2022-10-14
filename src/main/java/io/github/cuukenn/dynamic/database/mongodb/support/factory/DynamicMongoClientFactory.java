package io.github.cuukenn.dynamic.database.mongodb.support.factory;

import com.mongodb.client.MongoClient;

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
}
