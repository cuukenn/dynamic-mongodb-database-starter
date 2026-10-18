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
 * 点cut 用 {@code AnnotationClassFilter(checkInherited = true)} / {@code AnnotatedElementUtils},
 * 它会搜父类**和接口**；解析器若用 {@code AnnotationUtils.getAnnotation}（INHERITED_ANNOTATIONS，不搜接口）就会
 * 「点cut 匹配、通知执行、上下文为空」——又一条静默回落默认源的路径。
 * 注解写在父类/接口上（Spring 的标准用法）必须和写在类上等价。
 */
class InheritedAnnotationResolutionTest {

    @DynamicMongo("slave")
    static class ReportBase {
        public void biz() {
        }
    }

    /**
     * 父类上有注解，子类重写了方法：最具体方法是子类的，注解只在父类上。
     */
    static class OverridingSubclass extends ReportBase {
        @Override
        public void biz() {
            record();
        }
    }

    @DynamicMongo("slave")
    interface ReportApi {
        void biz();
    }

    /**
     * 注解在接口上（类上、方法上都没有），是「面向接口编程 + AOP」的常见形态。
     */
    static class ReportApiImpl implements ReportApi {
        @Override
        public void biz() {
            record();
        }
    }

    interface AnnotatedMethodApi {
        @DynamicMongo("slave")
        void biz();
    }

    static class AnnotatedMethodApiImpl implements AnnotatedMethodApi {
        @Override
        public void biz() {
            record();
        }
    }

    private static final AtomicReference<DynamicMongoContext> SEEN = new AtomicReference<>();

    private static void record() {
        SEEN.set(DynamicMongoDatabaseContextHolder.peek());
    }

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
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class CglibConfig {
        @Bean
        OverridingSubclass overridingSubclass() {
            return new OverridingSubclass();
        }

        @Bean
        ReportApi reportApi() {
            return new ReportApiImpl();
        }

        @Bean
        AnnotatedMethodApi annotatedMethodApi() {
            return new AnnotatedMethodApiImpl();
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
    void resolvesAnnotationOnOverriddenSuperclassMethod() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CglibConfig.class)) {
            context.getBean(OverridingSubclass.class).biz();
        }
        assertSwitched("父类注解 + 子类重写方法");
    }

    @Test
    void resolvesClassLevelAnnotationOnInterface() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CglibConfig.class)) {
            context.getBean(ReportApi.class).biz();
        }
        assertSwitched("接口上的类级 @DynamicMongo");
    }

    @Test
    void resolvesMethodLevelAnnotationOnInterfaceMethod() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CglibConfig.class)) {
            context.getBean(AnnotatedMethodApi.class).biz();
        }
        assertSwitched("接口方法上的 @DynamicMongo");
    }
}
