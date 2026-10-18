package io.github.cuukenn.dynamic.database.mongodb;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicContextValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongo;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContextResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationAdvisor;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationInterceptor;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoClassResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DefaultDynamicMongoContext;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DynamicMongoDatabaseContextHolder;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 拦截器的栈卫生：push/proceed/poll 必须严格配对。
 * <p>
 * 关键点：类级注解会让「类上所有方法」都进切面，其中包括 {@code Object} 声明的 {@code toString/equals/hashCode}——
 * 解析器对这些方法返回空上下文，如果把空上下文也 push 进去，嵌套调用里就会把外层的真实上下文遮蔽掉（静默连错库）。
 */
class DynamicMongoAnnotationInterceptorTest {

    static final List<String> OBSERVED = new ArrayList<>();

    static class OuterService {
        private final InnerService innerService;

        OuterService(InnerService innerService) {
            this.innerService = innerService;
        }

        @DynamicMongo("slave")
        public String outer() {
            OBSERVED.add("outer=" + peekInstanceId());
            innerService.inner();
            OBSERVED.add("outer-after-inner=" + peekInstanceId());
            return "ok";
        }

        @DynamicMongo(value = "slave", databaseName = "slave_db")
        public void boom() {
            throw new IllegalStateException("target failed");
        }
    }

    static class InnerService {
        @DynamicMongo("report")
        public void inner() {
            OBSERVED.add("inner=" + peekInstanceId());
        }
    }

    /**
     * 空注解（两个属性都没写）：解析结果是「没有切换」，不应该遮蔽外层上下文。
     */
    @DynamicMongo
    static class NoSwitchService {
        public void noSwitch() {
            OBSERVED.add("noSwitch=" + peekInstanceId());
        }
    }

    /**
     * 只关心 instanceId 的桩解析器：原样返回字面量，未知表达式返回 null（等价于真实链里解不出来）。
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

    private static String peekInstanceId() {
        return DynamicMongoDatabaseContextHolder.peek() == null
            ? null
            : DynamicMongoDatabaseContextHolder.peek().getInstanceId();
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {
        @Bean
        InnerService innerService() {
            return new InnerService();
        }

        @Bean
        OuterService outerService(InnerService innerService) {
            return new OuterService(innerService);
        }

        @Bean
        NoSwitchService noSwitchService() {
            return new NoSwitchService();
        }

        @Bean
        Advisor mongoAdvisor() {
            return new DynamicMongoAnnotationAdvisor(
                new DynamicMongoAnnotationInterceptor(new DynamicMongoClassResolver(true), LITERAL),
                DynamicMongo.class);
        }
    }

    @AfterEach
    void cleanUp() {
        OBSERVED.clear();
        DynamicMongoDatabaseContextHolder.clear();
    }

    @Test
    void nestedSwitchPushesAndPopsInOrder() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            context.getBean(OuterService.class).outer();
        }

        assertThat(OBSERVED).containsExactly("outer=slave", "inner=report", "outer-after-inner=slave");
        assertThat(DynamicMongoDatabaseContextHolder.peek()).isNull();
    }

    @Test
    void popsContextWhenTargetThrows() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            OuterService service = context.getBean(OuterService.class);
            assertThatThrownBy(service::boom).isInstanceOf(IllegalStateException.class).hasMessage("target failed");
        }

        assertThat(DynamicMongoDatabaseContextHolder.peek()).isNull();
    }

    @Test
    void doesNotPushContextWhenResolutionFails() {
        DynamicMongoContextResolver failing = (method, target) -> {
            throw new IllegalStateException("resolver failed");
        };
        DynamicMongoAnnotationInterceptor interceptor =
            new DynamicMongoAnnotationInterceptor(failing, LITERAL);
        MethodInvocation invocation = MethodInvocationStub.of(new OuterService(new InnerService()), "outer");

        assertThatThrownBy(() -> interceptor.invoke(invocation))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("resolver failed");

        assertThat(DynamicMongoDatabaseContextHolder.peek()).isNull();
    }

    @Test
    void emptyContextDoesNotShadowOuterContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            NoSwitchService service = context.getBean(NoSwitchService.class);

            DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("tenant_a", "tenant_db"));
            try {
                service.noSwitch();
            } finally {
                DynamicMongoDatabaseContextHolder.poll();
            }
        }

        assertThat(OBSERVED).containsExactly("noSwitch=tenant_a");
    }

    @Test
    void doesNotShadowOuterContextWithEmptyContextOfObjectMethods() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            OuterService service = context.getBean(OuterService.class);

            DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("tenant_a", "tenant_db"));
            try {
                // Object 方法在类级注解下同样进入切面，解析结果是空上下文
                service.toString();
                assertThat(peekInstanceId()).as("toString() 不能遮蔽外层上下文").isEqualTo("tenant_a");

                service.hashCode();
                assertThat(peekInstanceId()).as("hashCode() 不能遮蔽外层上下文").isEqualTo("tenant_a");

                service.equals(service);
                assertThat(peekInstanceId()).as("equals() 不能遮蔽外层上下文").isEqualTo("tenant_a");
            } finally {
                DynamicMongoDatabaseContextHolder.poll();
            }
        }
    }
}
