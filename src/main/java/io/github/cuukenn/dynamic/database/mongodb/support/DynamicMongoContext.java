package io.github.cuukenn.dynamic.database.mongodb.support;

/**
 * @author changgg
 */
public interface DynamicMongoContext {
    /**
     * 获取实例ID
     *
     * @return 实例ID
     */
    String getInstanceId();

    /**
     * 获取库名
     *
     * @return 库名
     */
    String getDatabase();
}
