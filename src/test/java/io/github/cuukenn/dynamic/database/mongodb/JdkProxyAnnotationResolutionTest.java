package io.github.cuukenn.dynamic.database.mongodb;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicContextValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongo;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationAdvisor;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationInterceptor;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoClassResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DynamicMongoDatabaseContextHolder;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 注解写在实现类/实现方法上、Bean 以接口类型暴露（JDK 动态代理）时，动态数据源必须仍然生效。
 * <p>
 * 这是最容易静默失败的一类场景：Advisor 的方法匹配器用 {@code AopUtils.getMostSpecificMethod} 处理了
 * 「方法可能声明在接口上」，但解析器只看 {@code method}/{@code method.getDeclaringClass()}，
 * 于是通知执行了、上下文却是空的——不报错，直接连回默认源。
 * Spring 对「实现了接口的 Bean」默认就用 JDK 动态代理，所以这是默认写法而不是边缘用法。
 */
class JdkProxyAnnotationResolutionTest {

    interface Foo {
        void bar();
    }

    private static final AtomicReference<DynamicMongoContext> SEEN = new AtomicReference<>();

    private static void record() {
        SEEN.set(DynamicMongoDatabaseContextHolder.peek());
    }

    @DynamicMongo("slave")
    static class ClassLevelImpl implements Foo {
        @Override
        public void bar() {
            record();
        }
    }

    static class MethodLevelImpl implements Foo {
        @Override
        @DynamicMongo("slave")
        public void bar() {
            record();
        }
    }

    /**
     * 只需字面量取值语义：注解里写的是 instanceId 字面量，真实环境的 SpEL 兜底解析器同样原样返回。
     */
    private static final DynamicContextValueParser LITERAL = new DynamicContextValueParser() {
        @Override
        public boolean match(String value) {
            return true;
        }

        @Override
        public String parse(MethodInvocation invocation, String value) {
            return value;
        }
    };

    private static Advisor advisor() {
        return new DynamicMongoAnnotationAdvisor(
            new DynamicMongoAnnotationInterceptor(new DynamicMongoClassResolver(true), LITERAL),
            DynamicMongo.class);
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class JdkProxyConfig {
        @Bean
        Foo classLevel() {
            return new ClassLevelImpl();
        }

        @Bean
        Foo methodLevel() {
            return new MethodLevelImpl();
        }

        @Bean
        Advisor mongoAdvisor() {
            return advisor();
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class CglibConfig {
        @Bean
        ClassLevelImpl classLevel() {
            return new ClassLevelImpl();
        }

        @Bean
        MethodLevelImpl methodLevel() {
            return new MethodLevelImpl();
        }

        @Bean
        Advisor mongoAdvisor() {
            return advisor();
        }
    }

    private static void assertSwitched(String scenario) {
        DynamicMongoContext context = SEEN.get();
        assertNotNull(context, scenario + ": 通知没有执行（没有生成代理？）");
        assertEquals("slave", context.getInstanceId(), scenario);
    }

    @Test
    void resolvesClassLevelAnnotationThroughJdkProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(JdkProxyConfig.class)) {
            context.getBean("classLevel", Foo.class).bar();
        }
        assertSwitched("JDK 代理 + 实现类上的类级 @DynamicMongo");
    }

    @Test
    void resolvesMethodLevelAnnotationThroughJdkProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(JdkProxyConfig.class)) {
            context.getBean("methodLevel", Foo.class).bar();
        }
        assertSwitched("JDK 代理 + 实现方法上的方法级 @DynamicMongo");
    }

    @Test
    void resolvesClassLevelAnnotationThroughCglibProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CglibConfig.class)) {
            context.getBean(ClassLevelImpl.class).bar();
        }
        assertSwitched("CGLIB 代理 + 实现类上的类级 @DynamicMongo");
    }

    @Test
    void resolvesMethodLevelAnnotationThroughCglibProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CglibConfig.class)) {
            context.getBean(MethodLevelImpl.class).bar();
        }
        assertSwitched("CGLIB 代理 + 实现方法上的方法级 @DynamicMongo");
    }
}
