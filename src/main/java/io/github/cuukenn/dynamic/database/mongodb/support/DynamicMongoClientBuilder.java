package io.github.cuukenn.dynamic.database.mongodb.support;

import com.mongodb.client.MongoClient;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;

/**
 * @author changgg
 */
public interface DynamicMongoClientBuilder {
    /**
     * 根据配置创建MongoClient客户端
     *
     * @param instanceId 实例ID
     * @param properties 配置
     * @return 客户端
     */
    MongoClient build(String instanceId, MongoProperties properties);
}
