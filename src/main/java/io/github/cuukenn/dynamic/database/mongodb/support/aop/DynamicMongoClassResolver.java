package io.github.cuukenn.dynamic.database.mongodb.support.aop;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongo;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContextResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DefaultDynamicMongoContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodClassKey;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源解析器
 *
 * @author changgg
 */
public class DynamicMongoClassResolver implements DynamicMongoContextResolver {
    private static final Logger log = LoggerFactory.getLogger(DynamicMongoClassResolver.class);
    /**
     * 缓存方法对应的注解原始表达式（不可变使用：只写入、对外返回副本）
     */
    private final Map<Object, DynamicMongoContext> contextCache = new ConcurrentHashMap<>();
    private final boolean allowedPublicOnly;
    private final Object lock = new Object();

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
        final boolean isDebugEnabled = log.isDebugEnabled();
        if (method.getDeclaringClass() == Object.class) {
            if (isDebugEnabled) {
                log.debug("the method belongs to Object class,skip it");
            }
            return new DefaultDynamicMongoContext();
        }
        Object cacheKey = new MethodClassKey(method, targetObject.getClass());
        DynamicMongoContext context = this.contextCache.get(cacheKey);
        if (context == null) {
            if (isDebugEnabled) {
                log.debug("not found in cache try second lock");
            }
            synchronized (lock) {
                context = this.contextCache.get(cacheKey);
                if (context == null) {
                    context = resolveContext(method);
                    if (context == null) {
                        if (isDebugEnabled) {
                            log.debug("cant not resolve the context,just create default");
                        }
                        context = new DefaultDynamicMongoContext();
                    }
                    if (isDebugEnabled) {
                        log.debug("cache context:{}", context);
                    }
                    this.contextCache.put(cacheKey, context);
                }
            }
        }
        //缓存只保存注解的原始表达式; 每次返回独立副本,
        //防止 parseValue 原地改写导致缓存污染(动态取值只在首次生效)及并发调用互相覆盖
        return new DefaultDynamicMongoContext(context.getInstanceId(), context.getDatabase());
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
    protected DynamicMongoContext resolveContext(Method method) {
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
