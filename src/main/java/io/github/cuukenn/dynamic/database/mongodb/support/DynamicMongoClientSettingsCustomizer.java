package io.github.cuukenn.dynamic.database.mongodb.support;

import com.mongodb.MongoClientSettings;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;

/**
 * @author changgg
 */
@FunctionalInterface
public interface DynamicMongoClientSettingsCustomizer {
    /**
     * 支持主库和从库进行一些特殊操作
     *
     * @param instanceId 实例ID
     * @param dynamic    动态库
     * @param properties 配置
     */
    void customize(String instanceId, MongoClientSettings.Builder dynamic, MongoProperties properties);
}
