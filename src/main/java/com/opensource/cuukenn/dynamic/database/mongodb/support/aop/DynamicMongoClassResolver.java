package com.opensource.cuukenn.dynamic.database.mongodb.support.aop;

import com.opensource.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import org.springframework.core.MethodClassKey;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据源解析器
 *
 * @author changgg
 */
public class DynamicMongoClassResolver {
    /**
     * 缓存方法对应的数据源
     */
    private final Map<Object, DynamicMongoContext> dsCache = new HashMap<>();
    private final boolean allowedPublicOnly;

    /**
     * 加入扩展, 给外部一个修改aop条件的机会
     *
     * @param allowedPublicOnly 只允许公共的方法, 默认为true
     */
    public DynamicMongoClassResolver(boolean allowedPublicOnly) {
        this.allowedPublicOnly = allowedPublicOnly;
    }

    /**
     * 从缓存获取数据
     *
     * @param method       方法
     * @param targetObject 目标对象
     * @return ds
     */
    public DynamicMongoContext findKey(Method method, Object targetObject) {
        if (method.getDeclaringClass() == Object.class) {
            return new DynamicMongoContext();
        }
        Object cacheKey = new MethodClassKey(method, targetObject.getClass());
        DynamicMongoContext context = this.dsCache.get(cacheKey);
        if (context == null) {
            synchronized (DynamicMongoClassResolver.class) {
                context = this.dsCache.get(cacheKey);
                if (context == null) {
                    context = computeContext(method, targetObject);
                    if (context == null) {
                        context = new DynamicMongoContext();
                    }
                    this.dsCache.put(cacheKey, context);
                }
            }
        }
        return context;
    }

    /**
     * 查找注解的顺序
     * 1. 当前方法
     * 2. 桥接方法
     * 3. 当前类开始一直找到Object
     * 4. 支持mybatis-plus, mybatis-spring
     *
     * @param method       方法
     * @param targetObject 目标对象
     * @return context
     */
    private DynamicMongoContext computeContext(Method method, Object targetObject) {
        if (allowedPublicOnly && !Modifier.isPublic(method.getModifiers())) {
            return null;
        }
        DynamicMongo annotation = AnnotationUtils.getAnnotation(method, DynamicMongo.class);
        if (annotation == null) {
            return null;
        }
        return new DynamicMongoContext(annotation.instanceId(), annotation.databaseName());
    }
}
