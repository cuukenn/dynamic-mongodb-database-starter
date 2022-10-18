package io.github.cuukenn.dynamic.database.mongodb.support.context.parser;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicContextValueParser;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author changgg
 */
public abstract class AbstractChainValueParser implements DynamicContextValueParser {
    private static final Logger log = LoggerFactory.getLogger(AbstractChainValueParser.class);
    private DynamicContextValueParser nextParser;

    public void setNextParser(DynamicContextValueParser nextParser) {
        this.nextParser = nextParser;
    }

    @Override
    public String parse(MethodInvocation invocation, String value) {
        final boolean isDebugEnabled = log.isDebugEnabled();
        if (isDebugEnabled) {
            log.debug("invocation:{},value:{}", invocation, value);
        }
        String parsedValue = null;
        if (match(value)) {
            parsedValue = doParse(invocation, value);
        }
        if (parsedValue == null && nextParser != null) {
            return nextParser.parse(invocation, value);
        } else {
            return parsedValue;
        }
    }

    /**
     * 转化数据
     *
     * @param invocation invocation
     * @param value      value
     * @return 数据
     */
    protected abstract String doParse(MethodInvocation invocation, String value);
}
