package io.github.cuukenn.dynamic.database.mongodb.support;

import org.aopalliance.intercept.MethodInvocation;

/**
 * @author changgg
 */
public interface DynamicContextValueParser {
    /**
     * 是否可以处理此类型
     *
     * @param value 待处理字符串
     * @return 是否支持
     */
    boolean match(String value);

    /**
     * 转化数值
     *
     * @param invocation 执行器
     * @param value        待处理字符串
     * @return 处理完毕的字符串
     */
    String parse(MethodInvocation invocation, String value);
}
