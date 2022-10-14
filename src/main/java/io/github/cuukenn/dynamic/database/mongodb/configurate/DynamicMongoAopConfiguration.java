package io.github.cuukenn.dynamic.database.mongodb.configurate;

import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongodbProperties;
import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoAopProperties;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongo;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationAdvisor;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

/**
 * @author changgg
 */
@Configuration
public class DynamicMongoAopConfiguration {
    private final Logger logger = LoggerFactory.getLogger(DynamicMongoAopConfiguration.class);

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public Advisor dynamicMongoClientAnnotationAdvisor(DynamicMongodbProperties properties) {
        logger.info("register dynamic mongo client aop");
        DynamicMongoAopProperties aopProperties = properties.getAop();
        DynamicMongoAnnotationInterceptor interceptor = new DynamicMongoAnnotationInterceptor(aopProperties.getAllowedPublicOnly());
        DynamicMongoAnnotationAdvisor advisor = new DynamicMongoAnnotationAdvisor(interceptor, DynamicMongo.class);
        advisor.setOrder(aopProperties.getOrder());
        return advisor;
    }
}
