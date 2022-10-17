package io.github.cuukenn.dynamic.database.mongodb.support.factory;

import com.mongodb.client.MongoClient;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;

/**
 * @author changgg
 */
public interface DynamicMongoClientBuilder {
    /**
     * 根据配置创建MongoClient客户端
     *
     * @param properties 配置
     * @return 客户端
     */
    MongoClient build(MongoProperties properties);
}
