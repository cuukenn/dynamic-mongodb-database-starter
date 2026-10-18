package io.github.cuukenn.dynamic.database.mongodb.support.context;

import io.github.cuukenn.dynamic.database.mongodb.MethodInvocationStub;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicContextValueParser;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 动态取值解不出来时必须**快速失败**：回落默认源意味着租户的读写悄悄落到默认集群/库上，
 * 这是多租户隔离最不能有的失败模式（README 也是这么承诺的）。
 * 区分两种情况：注解本来就没写（合法，用默认源）与写了解析不出来的表达式（必须报错）。
 */
class DefaultDynamicMongoContextTest {

    static class Target {
        public void biz() {
        }
    }

    private final MethodInvocation invocation = MethodInvocationStub.of(new Target(), "biz");

    private static DynamicContextValueParser parserReturning(String value) {
        return new DynamicContextValueParser() {
            @Override
            public boolean match(String candidate) {
                return true;
            }

            @Override
            public String parse(MethodInvocation candidate, String raw) {
                return value;
            }
        };
    }

    private static DynamicContextValueParser identityParser() {
        return new DynamicContextValueParser() {
            @Override
            public boolean match(String candidate) {
                return true;
            }

            @Override
            public String parse(MethodInvocation candidate, String raw) {
                return raw;
            }
        };
    }

    /**
     * 模拟真实的 SpEL 兜底行为：表达式求值为 null（非表达式字面量仍原样返回）。
     */
    private static DynamicContextValueParser parserFailingForDynamicValues() {
        return new DynamicContextValueParser() {
            @Override
            public boolean match(String candidate) {
                return true;
            }

            @Override
            public String parse(MethodInvocation candidate, String raw) {
                return raw.startsWith("#{") ? null : raw;
            }
        };
    }

    @Test
    void keepsLiteralValues() {
        DefaultDynamicMongoContext context = new DefaultDynamicMongoContext("slave", "report_db");
        context.parseValue(invocation, identityParser());

        assertEquals("slave", context.getInstanceId());
        assertEquals("report_db", context.getDatabase());
    }

    @Test
    void doesNotParseBlankFields() {
        DefaultDynamicMongoContext context = new DefaultDynamicMongoContext("", "");
        context.parseValue(invocation, new DynamicContextValueParser() {
            @Override
            public boolean match(String candidate) {
                return true;
            }

            @Override
            public String parse(MethodInvocation candidate, String raw) {
                throw new AssertionError("空字段不应该进入取值链");
            }
        });

        assertEquals("", context.getInstanceId());
        assertEquals("", context.getDatabase());
    }

    @Test
    void failsFastWhenInstanceIdResolvesToNull() {
        DefaultDynamicMongoContext context = new DefaultDynamicMongoContext("#{@tenant.current()}", "");

        assertThatThrownBy(() -> context.parseValue(invocation, parserReturning(null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("#{@tenant.current()}")
            .hasMessageContaining("instanceId")
            .hasMessageContaining("never falls back");
    }

    @Test
    void failsFastWhenInstanceIdResolvesToEmptyString() {
        DefaultDynamicMongoContext context = new DefaultDynamicMongoContext("#headerX-Tenant-Id", "");

        assertThatThrownBy(() -> context.parseValue(invocation, parserReturning("")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("#headerX-Tenant-Id")
            .hasMessageContaining("instanceId");
    }

    @Test
    void failsFastWhenDatabaseNameResolvesToNull() {
        DefaultDynamicMongoContext context = new DefaultDynamicMongoContext("slave", "#{@tenant.databaseOrNull()}");

        assertThatThrownBy(() -> context.parseValue(invocation, parserFailingForDynamicValues()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("databaseName")
            .hasMessageContaining("#{@tenant.databaseOrNull()}");
    }

    @Test
    void failureMessageNamesTheMethod() {
        DefaultDynamicMongoContext context = new DefaultDynamicMongoContext("#{@tenant.current()}", "");

        assertThatThrownBy(() -> context.parseValue(invocation, parserReturning(null)))
            .hasMessageContaining("biz");
    }

    @Test
    void keepsInstanceIdWhenOnlyDatabaseIsDynamic() {
        DefaultDynamicMongoContext context = new DefaultDynamicMongoContext("slave", "#headerX-Db");
        context.parseValue(invocation, identityParser());

        assertThat(context.getInstanceId()).isEqualTo("slave");
        assertThat(context.getDatabase()).isEqualTo("#headerX-Db");
    }
}
