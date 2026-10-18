package io.github.cuukenn.dynamic.database.mongodb.support.context.parser;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * @author changgg
 */
public class HeaderValueParser extends AbstractChainValueParser {
    /**
     * header prefix
     */
    public static final String HEADER_PREFIX = "#header";

    @Override
    public boolean match(String value) {
        return value.startsWith(HEADER_PREFIX);
    }

    @Override
    public String doParse(MethodInvocation invocation, String value) {
        final String headerName = value.substring(HEADER_PREFIX.length());
        final String header = currentRequest(value).getHeader(headerName);
        if (header == null) {
            throw new IllegalStateException("cannot resolve dynamic value '" + value + "': request header '" + headerName + "' is missing");
        }
        return header;
    }

    /**
     * 取不到当前请求时要给出可读的原因：注解写在非请求线程（异步任务、定时任务）上是最常见的误用。
     */
    private static HttpServletRequest currentRequest(String value) {
        final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            throw new IllegalStateException("cannot resolve dynamic value '" + value
                + "': there is no current HTTP request (#header values can only be resolved on a request thread)");
        }
        return ((ServletRequestAttributes) requestAttributes).getRequest();
    }
}
