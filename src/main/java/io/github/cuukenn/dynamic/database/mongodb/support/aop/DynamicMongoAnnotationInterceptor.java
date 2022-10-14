package io.github.cuukenn.dynamic.database.mongodb.support.aop;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import io.github.cuukenn.dynamic.database.mongodb.support.toolkit.DynamicMongoDatabaseContextHolder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author changgg
 */
public class DynamicMongoAnnotationInterceptor implements MethodInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(DynamicMongoAnnotationInterceptor.class);
    private final DynamicMongoClassResolver resolver;

    public DynamicMongoAnnotationInterceptor(Boolean allowedPublicOnly) {
        this.resolver = new DynamicMongoClassResolver(allowedPublicOnly);
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
        return resolver.findKey(invocation.getMethod(), invocation.getThis());
    }
}
