package io.github.cuukenn.dynamic.database.mongodb.support;

import org.springframework.boot.autoconfigure.mongo.MongoProperties;

/**
 * @author changgg
 */
@FunctionalInterface
public interface DynamicMongoClientPropertiesCustomizer {
    /**
     * 支持主库和从库进行一些特殊操作
     *
     * @param primary    主库
     * @param instanceId 实例ID
     * @param dynamic    动态库
     */
    void customize(MongoProperties primary, String instanceId, MongoProperties dynamic);
}
