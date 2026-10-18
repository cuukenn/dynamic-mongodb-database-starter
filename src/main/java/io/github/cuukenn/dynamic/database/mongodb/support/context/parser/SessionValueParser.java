package io.github.cuukenn.dynamic.database.mongodb.support.context.parser;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * @author changgg
 */
public class SessionValueParser extends AbstractChainValueParser {

    /**
     * Session prefix
     */
    public static final String SESSION_PREFIX = "#session";

    @Override
    public boolean match(String value) {
        return value.startsWith(SESSION_PREFIX);
    }

    @Override
    public String doParse(MethodInvocation invocation, String value) {
        final String attributeName = value.substring(SESSION_PREFIX.length());
        final Object attribute = currentRequest(value).getSession().getAttribute(attributeName);
        if (attribute == null) {
            throw new IllegalStateException("cannot resolve dynamic value '" + value + "': session attribute '" + attributeName + "' is missing");
        }
        return attribute.toString();
    }

    /**
     * 取不到当前请求时要给出可读的原因：注解写在非请求线程（异步任务、定时任务）上是最常见的误用。
     */
    private static HttpServletRequest currentRequest(String value) {
        final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            throw new IllegalStateException("cannot resolve dynamic value '" + value
                + "': there is no current HTTP request (#session values can only be resolved on a request thread)");
        }
        return ((ServletRequestAttributes) requestAttributes).getRequest();
    }
}
