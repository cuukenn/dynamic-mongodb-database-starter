package io.github.cuukenn.dynamic.database.mongodb.support.aop;

import io.github.cuukenn.dynamic.database.mongodb.support.context.DynamicMongoContext;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContextResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DynamicMongoDatabaseContextHolder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author changgg
 */
public class DynamicMongoAnnotationInterceptor implements MethodInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(DynamicMongoAnnotationInterceptor.class);
    private final DynamicMongoContextResolver resolver;

    public DynamicMongoAnnotationInterceptor(DynamicMongoContextResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        final DynamicMongoContext context = DynamicMongoDatabaseContextHolder.push(determineDatabaseInstance(invocation));
        if (logger.isDebugEnabled()) {
            logger.debug("excepted dynamic mongodb instance is [{}]", context);
        }
        try {
            return invocation.proceed();
        } finally {
            DynamicMongoDatabaseContextHolder.poll();
        }
    }

    private DynamicMongoContext determineDatabaseInstance(MethodInvocation invocation) {
        return resolver.resolve(invocation.getMethod(), invocation.getThis());
    }
}
