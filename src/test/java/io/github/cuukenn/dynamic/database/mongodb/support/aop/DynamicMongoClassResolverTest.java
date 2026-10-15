package io.github.cuukenn.dynamic.database.mongodb.support.aop;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicContextValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongo;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
