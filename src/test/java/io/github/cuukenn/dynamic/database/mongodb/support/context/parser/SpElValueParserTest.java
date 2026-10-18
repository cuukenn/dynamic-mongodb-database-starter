package io.github.cuukenn.dynamic.database.mongodb.support.context.parser;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 取值表达式来自注解，是有限集合；每次调用都重新编译 SpEL 属于纯浪费（SpEL 编译不便宜，且这是在业务方法的拦截器里同步执行的）。
 * 缓存只允许缓存「编译结果」，绝不能缓存 EvaluationContext——它带本次调用的 rootObject（method/args/target）。
 */
class SpElValueParserTest {

    static class Target {
        public void biz() {
        }
    }

    private static class CountingExpressionParser implements ExpressionParser {
        private final AtomicInteger parses = new AtomicInteger();
        private final ExpressionParser delegate = new SpelExpressionParser();

        @Override
        public Expression parseExpression(String expressionString) {
            parses.incrementAndGet();
            return delegate.parseExpression(expressionString);
        }

        @Override
        public Expression parseExpression(String expressionString, ParserContext context) {
            parses.incrementAndGet();
            return delegate.parseExpression(expressionString, context);
        }
    }

    private static MethodInvocation invocation() throws NoSuchMethodException {
        final Method method = Target.class.getMethod("biz");
        return new MethodInvocation() {
            @Override
            public Object proceed() {
                return null;
            }

            @Override
            public Object getThis() {
                return new Target();
            }

            @Override
            public AccessibleObject getStaticPart() {
                return method;
            }

            @Override
            public Method getMethod() {
                return method;
            }

            @Override
            public Object[] getArguments() {
                return new Object[0];
            }
        };
    }

    @Test
    void compilesEachExpressionOnlyOnce() throws Exception {
        CountingExpressionParser counting = new CountingExpressionParser();
        SpElValueParser parser = new SpElValueParser();
        parser.setExpressionParser(counting);
        MethodInvocation invocation = invocation();

        for (int i = 0; i < 5; i++) {
            assertEquals("slave", parser.doParse(invocation, "#{'slave'}"), "第 " + i + " 次取值结果必须一致");
        }

        assertEquals(1, counting.parses.get(), "同一个表达式只应编译一次");
    }

    @Test
    void cachesDifferentExpressionsSeparately() throws Exception {
        CountingExpressionParser counting = new CountingExpressionParser();
        SpElValueParser parser = new SpElValueParser();
        parser.setExpressionParser(counting);
        MethodInvocation invocation = invocation();

        assertEquals("a", parser.doParse(invocation, "#{'a'}"));
        assertEquals("b", parser.doParse(invocation, "#{'b'}"));
        assertEquals("a", parser.doParse(invocation, "#{'a'}"));
        assertEquals("b", parser.doParse(invocation, "#{'b'}"));

        assertEquals(2, counting.parses.get(), "两个表达式各编译一次");
    }

    /**
     * 字面量（带 #header/#session 前缀的本来就不归它管）必须原样返回，模板里没有 #{} 时就是字面量。
     */
    @Test
    void returnsLiteralValueAsIs() throws Exception {
        SpElValueParser parser = new SpElValueParser();
        assertEquals("slave", parser.doParse(invocation(), "slave"));
        assertEquals("#headerTenant", parser.doParse(invocation(), "#headerTenant"));
    }

    @Test
    void matchAlwaysTrueSoItMustStayLastInChain() {
        assertEquals(true, new SpElValueParser().match("anything"));
    }
}
