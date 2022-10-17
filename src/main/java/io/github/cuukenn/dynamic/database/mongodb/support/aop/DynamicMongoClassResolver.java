package io.github.cuukenn.dynamic.database.mongodb.support.aop;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongo;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContextResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DefaultDynamicMongoContext;
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
public class DynamicMongoClassResolver implements DynamicMongoContextResolver {
    /**
     * 缓存方法对应的数据源
     */
    private final Map<Object, DynamicMongoContext> contextCache = new HashMap<>();
    private final boolean allowedPublicOnly;

    /**
     * 加入扩展, 给外部一个修改aop条件的机会
     *
     * @param allowedPublicOnly 只允许公共的方法, 默认为true
     */
    public DynamicMongoClassResolver(boolean allowedPublicOnly) {
        this.allowedPublicOnly = allowedPublicOnly;
    }

    @Override
    public DynamicMongoContext resolve(Method method, Object targetObject) {
        if (method.getDeclaringClass() == Object.class) {
            return new DefaultDynamicMongoContext();
        }
        Object cacheKey = new MethodClassKey(method, targetObject.getClass());
        DynamicMongoContext context = this.contextCache.get(cacheKey);
        if (context == null) {
            synchronized (DynamicMongoClassResolver.class) {
                context = this.contextCache.get(cacheKey);
                if (context == null) {
                    context = computeContext(method);
                    if (context == null) {
                        context = new DefaultDynamicMongoContext();
                    }
                    this.contextCache.put(cacheKey, context);
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
     * @param method 方法
     * @return context
     */
    private DynamicMongoContext computeContext(Method method) {
        if (allowedPublicOnly && !Modifier.isPublic(method.getModifiers())) {
            return null;
        }
        //先从方法上获取，获取不到则从类上获取
        DynamicMongo annotation = AnnotationUtils.getAnnotation(method, DynamicMongo.class);
        if (annotation == null) {
            annotation = AnnotationUtils.getAnnotation(method.getDeclaringClass(), DynamicMongo.class);
        }
        if (annotation == null) {
            return null;
        }
        return new DefaultDynamicMongoContext(annotation.instanceId(), annotation.databaseName());
    }
}
