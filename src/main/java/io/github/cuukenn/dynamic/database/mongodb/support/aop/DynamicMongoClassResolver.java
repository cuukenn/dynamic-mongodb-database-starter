package io.github.cuukenn.dynamic.database.mongodb.support.aop;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongo;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContextResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DefaultDynamicMongoContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodClassKey;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

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
        // 目标类必须取「真实」的类: JDK 动态代理的 getClass() 是生成的 $ProxyN, 注解不在它上面
        Class<?> targetClass = targetObject != null ? AopUtils.getTargetClass(targetObject) : method.getDeclaringClass();
        Object cacheKey = new MethodClassKey(method, targetClass);
        DynamicMongoContext context = this.contextCache.get(cacheKey);
        if (context == null) {
            if (isDebugEnabled) {
                log.debug("not found in cache try second lock");
            }
            synchronized (lock) {
                context = this.contextCache.get(cacheKey);
                if (context == null) {
                    context = resolveContext(method, targetClass);
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
        return context.copy();
    }

    /**
     * 查找注解：方法级（实现类方法 → 接口/父类方法）与类级（目标类 → 声明方法的那一层）分别取，
     * 再按字段合并 —— 方法级注解只覆盖它写了的值，没写的沿用类级注解；
     * 两边都没写的字段保持为空，表示「继承外层上下文」而不是「回到默认源」。
     * <p>
     * 必须用 {@link AnnotatedElementUtils}：它搜方法/类型的完整层级（父类 + 接口），
     * 与点cut 的 {@code AnnotationClassFilter(checkInherited = true)} / 方法匹配器查找范围一致。
     * 用 {@code AnnotationUtils.getAnnotation}（INHERITED_ANNOTATIONS）会漏掉接口上的注解，
     * 造成「点cut 匹配但上下文为空」的静默回落。
     *
     * @param method      方法（JDK 动态代理下是接口方法）
     * @param targetClass 目标类（可能是实现类，不是 $ProxyN）
     * @return context
     */
    protected DynamicMongoContext resolveContext(Method method, Class<?> targetClass) {
        if (allowedPublicOnly && !Modifier.isPublic(method.getModifiers())) {
            return null;
        }
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        DynamicMongo methodAnnotation = findMethodAnnotation(specificMethod, method);
        DynamicMongo classAnnotation = findClassAnnotation(targetClass, method.getDeclaringClass());
        if (methodAnnotation == null && classAnnotation == null) {
            return null;
        }
        //字段级合并：方法级注解只覆盖它写了的值，没写的沿用类级注解；两边都没写的保持为空（= 继承外层上下文/默认源）
        String instanceId = firstNonEmpty(instanceIdOf(methodAnnotation), instanceIdOf(classAnnotation));
        String database = firstNonEmpty(databaseOf(methodAnnotation), databaseOf(classAnnotation));
        return new DefaultDynamicMongoContext(instanceId, database);
    }

    /**
     * 方法级注解：实现类上的方法优先，其次是接口/父类上声明的方法。
     */
    private DynamicMongo findMethodAnnotation(Method specificMethod, Method method) {
        DynamicMongo annotation = AnnotatedElementUtils.findMergedAnnotation(specificMethod, DynamicMongo.class);
        if (annotation == null && specificMethod != method) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method, DynamicMongo.class);
        }
        return annotation;
    }

    /**
     * 类级注解：目标类优先，其次是声明方法的那一层（接口方法场景）。
     */
    private DynamicMongo findClassAnnotation(Class<?> targetClass, Class<?> declaringClass) {
        DynamicMongo annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, DynamicMongo.class);
        if (annotation == null && targetClass != declaringClass) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(declaringClass, DynamicMongo.class);
        }
        return annotation;
    }

    private static String instanceIdOf(DynamicMongo annotation) {
        return annotation == null ? null : annotation.instanceId();
    }

    private static String databaseOf(DynamicMongo annotation) {
        return annotation == null ? null : annotation.databaseName();
    }

    private static String firstNonEmpty(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
