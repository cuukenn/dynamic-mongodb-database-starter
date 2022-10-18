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
            if (StringUtils.hasText(this.instanceId)) {
                this.instanceId = valueParser.parse(invocation, this.instanceId);
            }
            if (StringUtils.hasText(this.database)) {
                this.database = valueParser.parse(invocation, this.database);
            }
        }
        if (isDebugEnabled) {
            log.debug("after parse context:{}", this);
        }
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
    public String toString() {
        return "DefaultDynamicMongoContext{" +
            "instanceId='" + instanceId + '\'' +
            ", database='" + database + '\'' +
            '}';
    }
}
