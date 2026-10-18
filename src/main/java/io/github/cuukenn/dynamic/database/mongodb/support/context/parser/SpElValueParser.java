package io.github.cuukenn.dynamic.database.mongodb.support.context.parser;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author changgg
 */
public class SpElValueParser extends AbstractChainValueParser {
    /**
     * 参数发现器
     */
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    /**
     * Express语法解析器
     */
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    /**
     * 解析上下文的模板
     */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>(16);
    private ParserContext parserContext = new TemplateParserContext();
    private BeanResolver beanResolver;
    private ExpressionParser expressionParser = PARSER;

    @Override
    public boolean match(String value) {
        return true;
    }

    @Override
    public String doParse(MethodInvocation invocation, String key) {
        Method method = invocation.getMethod();
        Object[] arguments = invocation.getArguments();
        ExpressionRootObject rootObject = new ExpressionRootObject(method, arguments, invocation.getThis());
        StandardEvaluationContext context = new MethodBasedEvaluationContext(rootObject, method, arguments, NAME_DISCOVERER);
        context.setBeanResolver(beanResolver);
        Expression expression = expressionCache.computeIfAbsent(key, this::compileExpression);
        final Object value = expression.getValue(context);
        return value == null ? null : value.toString();
    }

    /**
     * 编译表达式。注解里的取值表达式是有限集合，编译结果可以安全缓存（实例级，含注解驱动的固定字符串）；
     * EvaluationContext 绝不能缓存——它携带本次调用的 method/args/target。
     */
    private Expression compileExpression(String expressionString) {
        return parserContext != null
            ? expressionParser.parseExpression(expressionString, parserContext)
            : expressionParser.parseExpression(expressionString);
    }

    public void setParserContext(ParserContext parserContext) {
        this.parserContext = parserContext;
        this.expressionCache.clear();
    }

    public void setBeanResolver(BeanResolver beanResolver) {
        this.beanResolver = beanResolver;
    }

    public void setExpressionParser(ExpressionParser expressionParser) {
        this.expressionParser = expressionParser;
        this.expressionCache.clear();
    }

    public static class ExpressionRootObject {
        private final Method method;

        private final Object[] args;

        private final Object target;

        public ExpressionRootObject(Method method, Object[] args, Object target) {
            this.method = method;
            this.args = args;
            this.target = target;
        }

        public Method getMethod() {
            return method;
        }

        public Object[] getArgs() {
            return args;
        }

        public Object getTarget() {
            return target;
        }
    }
}
