package io.github.cuukenn.dynamic.database.mongodb.configurate;

import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoAopProperties;
import io.github.cuukenn.dynamic.database.mongodb.properties.DynamicMongoProperties;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.context.expression.BeanFactoryResolver;

/**
 * @author changgg
 */
@Configuration(proxyBeanMethods = false)
public class DynamicMongoAopConfiguration {
    private final Logger logger = LoggerFactory.getLogger(DynamicMongoAopConfiguration.class);

    /**
     * 注册注解拦截 Advisor。开关：spring.data.mongodb.dynamic.aop.enabled=false 时不注册（默认注册），
     * 注册器之外的取值链/解析器 Bean 仍会装配，但没有 Advisor 就不会生成代理、拦截失效。
     * <p>
     * 条件必须带类型（DynamicMongoAnnotationAdvisor）：只写 @ConditionalOnMissingBean 会按方法返回类型 Advisor 判断，
     * 容器里只要有任意别的 Advisor（Boot 应用里是常态）就会把我们的 Advisor 一起关掉。
     */
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    @ConditionalOnProperty(prefix = DynamicMongoProperties.PREFIX + ".aop", name = "enabled",
        havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(DynamicMongoAnnotationAdvisor.class)
    public Advisor dynamicMongoClientAnnotationAdvisor(DynamicMongoProperties properties, DynamicMongoContextResolver resolver, DynamicContextValueParser valueParser) {
        logger.info("register dynamic mongo client aop");
        DynamicMongoAopProperties aopProperties = properties.getAop();
        DynamicMongoAnnotationInterceptor interceptor = new DynamicMongoAnnotationInterceptor(resolver, valueParser);
        DynamicMongoAnnotationAdvisor advisor = new DynamicMongoAnnotationAdvisor(interceptor, DynamicMongo.class);
        advisor.setOrder(aopProperties.getOrder());
        return advisor;
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicMongoContextResolver dynamicMongoClassResolver(DynamicMongoProperties properties) {
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
        return onlySpElValueParser(beanFactory);
    }

    /**
     * 非 Web 应用（WebApplicationType.NONE）也需要取值链，否则没有 DynamicContextValueParser Bean，
     * Advisor 无法装配，应用启动直接失败。此分支只提供 SpEL（没有 request/session 可用）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnNotWebApplication
    public DynamicContextValueParser dynamicContextParserOnNotWebApplication(BeanFactory beanFactory) {
        logger.info("register dynamic mongo context value parser for not web application");
        return onlySpElValueParser(beanFactory);
    }

    private SpElValueParser onlySpElValueParser(BeanFactory beanFactory) {
        SpElValueParser spElValueParser = new SpElValueParser();
        spElValueParser.setBeanResolver(new BeanFactoryResolver(beanFactory));
        return spElValueParser;
    }
}
