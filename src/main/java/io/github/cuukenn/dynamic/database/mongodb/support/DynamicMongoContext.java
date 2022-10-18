package io.github.cuukenn.dynamic.database.mongodb.support;

import org.aopalliance.intercept.MethodInvocation;

/**
 * @author changgg
 */
public interface DynamicMongoContext {
    /**
     * 数值转化处理器
     *
     * @param invocation  函数上下文
     * @param valueParser 执行器
     */
    default void parseValue(MethodInvocation invocation, DynamicContextValueParser valueParser) {
    }

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
