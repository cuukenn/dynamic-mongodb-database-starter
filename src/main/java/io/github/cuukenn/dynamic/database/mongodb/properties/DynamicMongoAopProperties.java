package io.github.cuukenn.dynamic.database.mongodb.properties;

import org.springframework.core.Ordered;

/**
 * 多数据源aop相关配置
 *
 * @author TaoYu
 */
public class DynamicMongoAopProperties {

    /**
     * enabled default DS annotation default true
     */
    private Boolean enabled = true;
    /**
     * aop order
     */
    private Integer order = Ordered.HIGHEST_PRECEDENCE;
    /**
     * aop allowedPublicOnly
     */
    private Boolean allowedPublicOnly = true;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public Boolean getAllowedPublicOnly() {
        return allowedPublicOnly;
    }

    public void setAllowedPublicOnly(Boolean allowedPublicOnly) {
        this.allowedPublicOnly = allowedPublicOnly;
    }
}
