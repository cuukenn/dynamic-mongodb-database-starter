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

    /**
     * 返回一个「可以安全改写」的副本。
     * <p>
     * resolver 会把解析结果缓存起来，每次调用返回副本——因为 {@link #parseValue} 会就地改写字段，
     * 直接返回缓存实例会让动态取值只在首次调用生效。
     * <p>
     * <b>自定义上下文实现必须覆盖本方法并返回自身类型</b>，否则副本会被降级成默认实现（{@code DefaultDynamicMongoContext}），
     * 你自己的 {@code parseValue} 永远不会被调用。
     *
     * @return 上下文副本
     */
    default DynamicMongoContext copy() {
        return new io.github.cuukenn.dynamic.database.mongodb.support.context.DefaultDynamicMongoContext(
            getInstanceId(), getDatabase());
    }
}
