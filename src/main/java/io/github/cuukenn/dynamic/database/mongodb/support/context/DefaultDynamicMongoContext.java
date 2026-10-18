package io.github.cuukenn.dynamic.database.mongodb.support.context;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicContextValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 上下文
 *
 * @author changgg
 */
public class DefaultDynamicMongoContext implements DynamicMongoContext {
    private static final Logger log = LoggerFactory.getLogger(DefaultDynamicMongoContext.class);
    /**
     * 实例ID
     */
    private String instanceId;
    /**
     * 库名
     */
    private String database;

    public DefaultDynamicMongoContext() {
        this.instanceId = "";
        this.database = "";
    }

    public DefaultDynamicMongoContext(String instanceId, String database) {
        this.instanceId = StringUtils.hasText(instanceId) ? instanceId : "";
        this.database = StringUtils.hasText(database) ? database : "";
    }

    @Override
    public void parseValue(MethodInvocation invocation, DynamicContextValueParser valueParser) {
        boolean isDebugEnabled = log.isDebugEnabled();
        if (isDebugEnabled) {
            log.debug("before parse context:{}", this);
        }
        if (valueParser != null) {
            this.instanceId = parseValue(invocation, valueParser, this.instanceId, "instanceId");
            this.database = parseValue(invocation, valueParser, this.database, "databaseName");
        }
        if (isDebugEnabled) {
            log.debug("after parse context:{}", this);
        }
    }

    /**
     * 解析单个字段：注解里没写（空）就保持空、交给默认源；写了却解析不出非空值则**快速失败**。
     * 静默回落默认源意味着这次调用的读写落到默认集群/库上，多租户隔离最不能有这种失败模式。
     *
     * @param invocation 被拦截的方法调用
     * @param valueParser 取值责任链
     * @param value 注解里写的原始值
     * @param attribute 注解属性名（用于报错）
     * @return 解析结果
     */
    private String parseValue(MethodInvocation invocation, DynamicContextValueParser valueParser, String value, String attribute) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String resolved = valueParser.parse(invocation, value);
        if (!StringUtils.hasText(resolved)) {
            throw new IllegalStateException("cannot resolve " + attribute + " '" + value + "' on method '"
                + invocation.getMethod().getDeclaringClass().getSimpleName() + '#' + invocation.getMethod().getName()
                + "': the dynamic value resolved to " + (resolved == null ? "null" : "an empty string")
                + "; @DynamicMongo never falls back to the default datasource when a dynamic value cannot be resolved");
        }
        return resolved;
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
        return new DefaultDynamicMongoContext(instanceId, database);
    }

    @Override
    public String toString() {
        return "DefaultDynamicMongoContext{" +
            "instanceId='" + instanceId + '\'' +
            ", database='" + database + '\'' +
            '}';
    }
}
