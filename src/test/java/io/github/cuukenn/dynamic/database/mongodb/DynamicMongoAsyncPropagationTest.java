package io.github.cuukenn.dynamic.database.mongodb;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicContextValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongo;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationAdvisor;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationInterceptor;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoClassResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DynamicMongoContextTaskDecorator;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DynamicMongoDatabaseContextHolder;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线程边界上的上下文传递（端到端）。
 * <p>
 * 真实会丢上下文的写法：<b>在 {@code @DynamicMongo} 方法内部</b>把活儿交给别的线程 ——
 * 调用一个 {@code @Async} 协作者、{@code CompletableFuture.supplyAsync}、或自己 submit 到线程池。
 * 提交发生在调用方线程（上下文还在），执行发生在池线程（ThreadLocal 是空的）→ 池线程上的 Mongo 访问
 * 静默落到默认源。声明 {@link DynamicMongoContextTaskDecorator} 后，上下文随任务一起过去。
 * </p>
 * <p>
 * 注意：{@code @Async} 与 {@code @DynamicMongo} 写在同一方法上不是这个场景 —— 实测那种写法的
 * {@code @Async} 通知在更外层，我们的切面是在池线程上执行的，上下文本来就正常（本测试不覆盖它）。
 * </p>
 */
class DynamicMongoAsyncPropagationTest {

    /** 没有 @DynamicMongo 的 @Async 协作者：入口方法带着上下文时，任务线程应当仍然看到该上下文。 */
    static class AsyncCollaborator {
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile String seenInstanceId;

        @Async
        public void work() {
            seenInstanceId = DynamicMongoDatabaseContextHolder.peekInstanceId();
            done.countDown();
        }

        String awaitSeenInstanceId() throws InterruptedException {
            assertTrue(done.await(5, TimeUnit.SECONDS), "异步任务没有在 5 秒内完成");
            return seenInstanceId;
        }
    }

    /** 入口：在自己的 @DynamicMongo 上下文里把活儿交给线程池 */
    static class EntryService {
        private final AsyncCollaborator collaborator;

        EntryService(AsyncCollaborator collaborator) {
            this.collaborator = collaborator;
        }

        @DynamicMongo("slave")
        public void doOuter() throws InterruptedException {
            collaborator.work();
            collaborator.awaitSeenInstanceId();
        }
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

    @Configuration
    @EnableAsync
    @EnableAspectJAutoProxy
    static class BaseConfig {
        @Bean
        AsyncCollaborator asyncCollaborator() {
            return new AsyncCollaborator();
        }

        @Bean
        EntryService entryService(AsyncCollaborator collaborator) {
            return new EntryService(collaborator);
        }

        @Bean
        ThreadPoolTaskExecutor taskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setTaskDecorator(taskDecorator());
            executor.initialize();
            return executor;
        }

        TaskDecorator taskDecorator() {
            return null;
        }

        @Bean
        Advisor mongoAdvisor() {
            DynamicMongoAnnotationAdvisor advisor = new DynamicMongoAnnotationAdvisor(
                new DynamicMongoAnnotationInterceptor(new DynamicMongoClassResolver(true), LITERAL), DynamicMongo.class);
            //与生产装配一致：默认 aop.order = HIGHEST_PRECEDENCE
            advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return advisor;
        }
    }

    @Configuration
    static class WithoutDecoratorConfig extends BaseConfig {
    }

    @Configuration
    static class WithDecoratorConfig extends BaseConfig {
        @Override
        TaskDecorator taskDecorator() {
            return new DynamicMongoContextTaskDecorator();
        }
    }

    @AfterEach
    void cleanup() {
        DynamicMongoDatabaseContextHolder.clear();
    }

    /**
     * 不装饰时的既有行为（限制）：池线程看不到上下文。这条是「特性记录」——它红了说明行为变了。
     */
    @Test
    void losesContextWithoutTaskDecorator() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(WithoutDecoratorConfig.class)) {
            AsyncCollaborator collaborator = context.getBean(AsyncCollaborator.class);
            context.getBean(EntryService.class).doOuter();

            assertNull(collaborator.awaitSeenInstanceId(),
                "未装饰时池线程看不到上下文（这就是需要 DynamicMongoContextTaskDecorator 的原因）");
        }
    }

    @Test
    void keepsContextWithTaskDecorator() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(WithDecoratorConfig.class)) {
            AsyncCollaborator collaborator = context.getBean(AsyncCollaborator.class);
            context.getBean(EntryService.class).doOuter();

            assertEquals("slave", collaborator.awaitSeenInstanceId(),
                "声明 DynamicMongoContextTaskDecorator 后，@Async 协作者必须仍在入口的 slave 上");
        }
    }
}
