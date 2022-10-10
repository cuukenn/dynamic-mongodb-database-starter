package com.opensource.cuukenn.dynamic.database.mongodb.support;

import org.springframework.util.StringUtils;

/**
 * 上下文
 *
 * @author changgg
 */
public class DynamicMongoContext {
    /**
     * 实例ID
     */
    private final String instanceId;
    /**
     * 库名
     */
    private final String databaseName;

    public DynamicMongoContext() {
        this.instanceId = "";
        this.databaseName = "";
    }

    public DynamicMongoContext(String instanceId, String databaseName) {
        this.instanceId = StringUtils.hasText(instanceId) ? instanceId : "";
        this.databaseName = StringUtils.hasText(databaseName) ? databaseName : "";
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public String toString() {
        return "DynamicMongoContext{" +
            "instanceId='" + instanceId + '\'' +
            ", databaseName='" + databaseName + '\'' +
            '}';
    }
}
