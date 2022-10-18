package io.github.cuukenn.dynamic.database.mongodb.configurate;

import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoAopProperties;
import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongodbProperties;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicContextValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongo;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContextResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationAdvisor;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationInterceptor;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoClassResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.context.parser.HeaderValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.context.parser.SessionValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.context.parser.SpElValueParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.context.expression.BeanFactoryResolver;

/**
 * @author changgg
 */
@Configuration
public class DynamicMongoAopConfiguration {
    private final Logger logger = LoggerFactory.getLogger(DynamicMongoAopConfiguration.class);

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public Advisor dynamicMongoClientAnnotationAdvisor(DynamicMongodbProperties properties, DynamicMongoContextResolver resolver, DynamicContextValueParser valueParser) {
        logger.info("register dynamic mongo client aop");
        DynamicMongoAopProperties aopProperties = properties.getAop();
        DynamicMongoAnnotationInterceptor interceptor = new DynamicMongoAnnotationInterceptor(resolver, valueParser);
        DynamicMongoAnnotationAdvisor advisor = new DynamicMongoAnnotationAdvisor(interceptor, DynamicMongo.class);
        advisor.setOrder(aopProperties.getOrder());
        return advisor;
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicMongoContextResolver dynamicMongoClassResolver(DynamicMongodbProperties properties) {
        logger.info("register dynamic mongo context resolver");
        return new DynamicMongoClassResolver(properties.getAop().getAllowedPublicOnly());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public DynamicContextValueParser dynamicContextParserOnServlet(BeanFactory beanFactory) {
        logger.info("register dynamic mongo context value parser on servlet");
        SpElValueParser spElValueParser = new SpElValueParser();
        spElValueParser.setBeanResolver(new BeanFactoryResolver(beanFactory));
        HeaderValueParser headerParser = new HeaderValueParser();
        SessionValueParser sessionParser = new SessionValueParser();
        headerParser.setNextParser(sessionParser);
        sessionParser.setNextParser(spElValueParser);
        return headerParser;
    }


    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.ANY)
    public DynamicContextValueParser dynamicContextParserOnAny(BeanFactory beanFactory) {
        logger.info("register dynamic mongo context value parser");
        SpElValueParser spElValueParser = new SpElValueParser();
        spElValueParser.setBeanResolver(new BeanFactoryResolver(beanFactory));
        return spElValueParser;
    }
}
