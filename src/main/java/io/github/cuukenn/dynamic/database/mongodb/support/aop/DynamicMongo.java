package io.github.cuukenn.dynamic.database.mongodb.support.aop;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author changgg
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DynamicMongo {
    /**
     * 数据源名称
     *
     * @return 名称
     */
    @AliasFor(value = "instanceId")
    String value() default "";

    /**
     * 实例ID
     *
     * @return 实例ID
     */
    @AliasFor(value = "value")
    String instanceId() default "";

    /**
     * 库名
     *
     * @return 库名
     */
    String databaseName() default "";
}
