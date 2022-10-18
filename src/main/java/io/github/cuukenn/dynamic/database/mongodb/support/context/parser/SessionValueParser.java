package io.github.cuukenn.dynamic.database.mongodb.support.context.parser;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

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
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        return request.getSession().getAttribute(value.substring(SESSION_PREFIX.length())).toString();
    }
}
