package io.github.cuukenn.dynamic.database.mongodb.support.context;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import org.springframework.util.StringUtils;

/**
 * 上下文
 *
 * @author changgg
 */
public class DefaultDynamicMongoContext implements DynamicMongoContext {
    /**
     * 实例ID
     */
    private final String instanceId;
    /**
     * 库名
     */
    private final String database;

    public DefaultDynamicMongoContext() {
        this.instanceId = "";
        this.database = "";
    }

    public DefaultDynamicMongoContext(String instanceId, String database) {
        this.instanceId = StringUtils.hasText(instanceId) ? instanceId : "";
        this.database = StringUtils.hasText(database) ? database : "";
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
