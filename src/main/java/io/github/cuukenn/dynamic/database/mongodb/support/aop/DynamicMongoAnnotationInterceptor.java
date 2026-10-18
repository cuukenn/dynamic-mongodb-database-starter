package io.github.cuukenn.dynamic.database.mongodb.support.aop;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicContextValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContextResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DynamicMongoDatabaseContextHolder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * @author changgg
 */
public class DynamicMongoAnnotationInterceptor implements MethodInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(DynamicMongoAnnotationInterceptor.class);
    private final DynamicMongoContextResolver resolver;
    private final DynamicContextValueParser valueParser;

    public DynamicMongoAnnotationInterceptor(DynamicMongoContextResolver resolver, DynamicContextValueParser valueParser) {
        this.resolver = resolver;
        this.valueParser = valueParser;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        final DynamicMongoContext context = determineDatabaseInstance(invocation);
        // 空上下文（注解没写任何值）代表「不切换」，不能 push：否则嵌套调用里会遮蔽外层的真实上下文
        final boolean switched = context != null
            && (StringUtils.hasText(context.getInstanceId()) || StringUtils.hasText(context.getDatabase()));
        if (switched) {
            DynamicMongoDatabaseContextHolder.push(context);
            if (logger.isDebugEnabled()) {
                logger.debug("excepted dynamic mongodb instance is [{}]", context);
            }
        }
        try {
            return invocation.proceed();
        } finally {
            if (switched) {
                DynamicMongoDatabaseContextHolder.poll();
            }
        }
    }

    private DynamicMongoContext determineDatabaseInstance(MethodInvocation invocation) {
        DynamicMongoContext context = resolver.resolve(invocation.getMethod(), invocation.getThis());
        if (context != null) {
            context.parseValue(invocation, valueParser);
        }
        return context;
    }
}
