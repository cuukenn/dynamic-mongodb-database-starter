package io.github.cuukenn.dynamic.database.mongodb.support;

import java.lang.reflect.Method;

/**
 * @author changgg
 */
public interface DynamicMongoContextResolver {
    /**
     * 解析上下文
     *
     * @param method       方法
     * @param targetObject 对象
     * @return 上下文
     */
    DynamicMongoContext resolve(Method method, Object targetObject);
}
