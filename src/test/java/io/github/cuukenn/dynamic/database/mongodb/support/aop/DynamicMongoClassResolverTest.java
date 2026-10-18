package io.github.cuukenn.dynamic.database.mongodb.support.aop;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicContextValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongo;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 回归测试：resolver 返回的上下文必须携带注解的原始表达式，
 * 且各次调用的 parseValue 互不影响（当前实现共享缓存实例、原地改写，导致
 * 动态取值只在首次调用生效、并发下互相覆盖）。
 */
class DynamicMongoClassResolverTest {

    static class Target {
        @DynamicMongo(instanceId = "#headerTenant", databaseName = "#headerDb")
        public void biz() {
        }
    }

    @DynamicMongo("classLevel")
    static class ClassAnnotatedTarget {
        public void withoutOwnAnnotation() {
        }

        @DynamicMongo("methodLevel")
        public void withOwnAnnotation() {
        }

        @DynamicMongo(databaseName = "archive_db")
        public void databaseOnly() {
        }

        void notPublic() {
        }
    }

    @DynamicMongo(databaseName = "class_db")
    static class ClassWithDatabaseOnly {
        @DynamicMongo("methodInstance")
        public void instanceOnly() {
        }
    }

    /**
     * 模拟真实取值链语义：#header 前缀 -> 取 header 值；否则 -> SpEL 模板按字面量原样返回。
     */
    private static DynamicContextValueParser headerish(final String headerValue) {
        return new DynamicContextValueParser() {
            @Override
            public boolean match(String value) {
                return value.startsWith("#header");
            }

            @Override
            public String parse(MethodInvocation invocation, String value) {
                return match(value) ? headerValue : value;
            }
        };
    }

    @Test
    void dynamicValuesAreReParsedForEveryInvocation() throws Exception {
        DynamicMongoClassResolver resolver = new DynamicMongoClassResolver(true);
        Method method = Target.class.getMethod("biz");
        Target target = new Target();

        // 请求 1：header 租户 = acme
        DynamicMongoContext first = resolver.resolve(method, target);
        first.parseValue(null, headerish("acme"));
        assertEquals("acme", first.getInstanceId());
        assertEquals("acme", first.getDatabase());

        // 请求 2：header 租户 = beta
        DynamicMongoContext second = resolver.resolve(method, target);
        assertEquals("#headerTenant", second.getInstanceId(),
            "resolve 返回的上下文必须是注解原始表达式，不得携带上一次的解析结果");
        assertEquals("#headerDb", second.getDatabase(),
            "resolve 返回的上下文必须是注解原始表达式，不得携带上一次的解析结果");

        second.parseValue(null, headerish("beta"));
        assertEquals("beta", second.getInstanceId(), "第二个请求必须用自己的 header 值");
        assertEquals("beta", second.getDatabase(), "第二个请求必须用自己的 header 值");

        // 请求 1 的上下文不因请求 2 的解析而被改写
        assertEquals("acme", first.getInstanceId(), "不同调用的上下文必须相互隔离");
    }

    @Test
    void classLevelAnnotationAppliesToMethodWithoutOwnAnnotation() throws Exception {
        DynamicMongoClassResolver resolver = new DynamicMongoClassResolver(true);
        DynamicMongoContext context = resolver.resolve(
            ClassAnnotatedTarget.class.getMethod("withoutOwnAnnotation"), new ClassAnnotatedTarget());

        assertEquals("classLevel", context.getInstanceId());
    }

    @Test
    void methodLevelAnnotationWinsOverClassLevel() throws Exception {
        DynamicMongoClassResolver resolver = new DynamicMongoClassResolver(true);
        DynamicMongoContext context = resolver.resolve(
            ClassAnnotatedTarget.class.getMethod("withOwnAnnotation"), new ClassAnnotatedTarget());

        assertEquals("methodLevel", context.getInstanceId());
    }

    /**
     * 方法级注解只覆盖它「写了」的字段：只写 databaseName 时 instanceId 沿用类级注解，
     * 而不是被重置成空（空意味着走默认集群，还会遮蔽外层实例）。
     */
    @Test
    void databaseOnlyMethodAnnotationKeepsClassLevelInstanceId() throws Exception {
        DynamicMongoClassResolver resolver = new DynamicMongoClassResolver(true);
        DynamicMongoContext context = resolver.resolve(
            ClassAnnotatedTarget.class.getMethod("databaseOnly"), new ClassAnnotatedTarget());

        assertEquals("archive_db", context.getDatabase());
        assertEquals("classLevel", context.getInstanceId(), "方法级只写 databaseName 时 instanceId 应沿用类级注解");
    }

    /**
     * 两个层级各写一半时字段级合并：方法级给 instanceId、类级给 databaseName。
     */
    @Test
    void mergesMethodLevelInstanceIdWithClassLevelDatabase() throws Exception {
        DynamicMongoClassResolver resolver = new DynamicMongoClassResolver(true);
        DynamicMongoContext context = resolver.resolve(
            ClassWithDatabaseOnly.class.getMethod("instanceOnly"), new ClassWithDatabaseOnly());

        assertEquals("methodInstance", context.getInstanceId());
        assertEquals("class_db", context.getDatabase());
    }

    @Test
    void skipsNonPublicMethodWhenAllowedPublicOnly() throws Exception {
        DynamicMongoClassResolver resolver = new DynamicMongoClassResolver(true);
        DynamicMongoContext context = resolver.resolve(
            ClassAnnotatedTarget.class.getDeclaredMethod("notPublic"), new ClassAnnotatedTarget());

        assertEquals("", context.getInstanceId(), "allowedPublicOnly 时应跳过非 public 方法（空上下文 = 不切换）");
    }

    @Test
    void resolvesNonPublicMethodWhenAllowedPublicOnlyDisabled() throws Exception {
        DynamicMongoClassResolver resolver = new DynamicMongoClassResolver(false);
        DynamicMongoContext context = resolver.resolve(
            ClassAnnotatedTarget.class.getDeclaredMethod("notPublic"), new ClassAnnotatedTarget());

        assertEquals("classLevel", context.getInstanceId());
    }

    /**
     * 自定义上下文实现（下游常见扩展：自己写 parseValue 做特殊取值）不能被 resolver 降级成默认实现，
     * 否则它的 parseValue 永远不会被调用。契约：自定义实现要覆盖 copy() 返回自身类型。
     */
    static class MarkerContext implements DynamicMongoContext {
        private final String instanceId;
        private final String database;
        private boolean parsed;

        MarkerContext(String instanceId, String database) {
            this.instanceId = instanceId;
            this.database = database;
        }

        @Override
        public void parseValue(MethodInvocation invocation, DynamicContextValueParser valueParser) {
            this.parsed = true;
        }

        @Override
        public String getInstanceId() {
            return instanceId;
        }

        @Override
        public String getDatabase() {
            return database;
        }

        @Override
        public DynamicMongoContext copy() {
            return new MarkerContext(instanceId, database);
        }
    }

    static class MarkerResolver extends DynamicMongoClassResolver {
        MarkerResolver() {
            super(true);
        }

        @Override
        protected DynamicMongoContext resolveContext(Method method, Class<?> targetClass) {
            DynamicMongo annotation = org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedAnnotation(method, DynamicMongo.class);
            return annotation == null ? null
                : new MarkerContext(annotation.instanceId(), annotation.databaseName());
        }
    }

    @Test
    void keepsCustomContextImplementationFromSubclassResolver() throws Exception {
        DynamicMongoContext context = new MarkerResolver().resolve(
            ClassAnnotatedTarget.class.getMethod("withOwnAnnotation"), new ClassAnnotatedTarget());

        assertEquals(MarkerContext.class, context.getClass(), "自定义上下文实现不能被降级成 DefaultDynamicMongoContext");

        context.parseValue(null, null);
        assertTrue(((MarkerContext) context).parsed, "自定义 parseValue 必须被调用到");
    }
}
