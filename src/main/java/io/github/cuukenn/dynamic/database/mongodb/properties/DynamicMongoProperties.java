package io.github.cuukenn.dynamic.database.mongodb.properties;

import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.Map;

/**
 * @author changgg
 */
@ConfigurationProperties(prefix = DynamicMongoProperties.PREFIX)
public class DynamicMongoProperties {
    public static final String PREFIX = "spring.data.mongodb.dynamic";
    private Map<String, MongoProperties> config;
    @NestedConfigurationProperty
    private DynamicMongoAopProperties aop = new DynamicMongoAopProperties();

    public Map<String, MongoProperties> getConfig() {
        return config;
    }

    public void setConfig(Map<String, MongoProperties> config) {
        this.config = config;
    }

    public DynamicMongoAopProperties getAop() {
        return aop;
    }

    public void setAop(DynamicMongoAopProperties aop) {
        this.aop = aop;
    }
}
