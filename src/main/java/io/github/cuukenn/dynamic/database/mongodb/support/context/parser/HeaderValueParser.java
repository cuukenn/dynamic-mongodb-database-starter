package io.github.cuukenn.dynamic.database.mongodb.support.context.parser;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

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
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        return request.getHeader(value.substring(HEADER_PREFIX.length()));
    }
}
