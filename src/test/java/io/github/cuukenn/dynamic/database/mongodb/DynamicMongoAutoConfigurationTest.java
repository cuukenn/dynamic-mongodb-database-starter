package io.github.cuukenn.dynamic.database.mongodb;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicContextValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongo;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationAdvisor;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoAnnotationInterceptor;
import io.github.cuukenn.dynamic.database.mongodb.support.aop.DynamicMongoClassResolver;
import io.github.cuukenn.dynamic.database.mongodb.support.context.DynamicMongoDatabaseContextHolder;
import io.github.cuukenn.dynamic.database.mongodb.support.context.parser.HeaderValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.context.parser.SpElValueParser;
import io.github.cuukenn.dynamic.database.mongodb.support.factory.DynamicMongoDatabaseFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装配顺序测试。
 * <p>
 * 本 starter 的 MongoDatabaseFactory 与 Boot 的 MongoDataAutoConfiguration 抢同一个 Bean
 * （双方都是 @ConditionalOnMissingBean(MongoDatabaseFactory.class)），因此必须显式声明装配顺序：
 * 依赖 Boot 排序在同 order 下按类名字母序兜底是隐式约定，一旦名字/包名或 Boot 行为变化就会静默失效
 * （不报错，只是动态切换整条链路失效）。
 *
 * @author changgg
 */
class DynamicMongoAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DynamicMongoAutoConfiguration.class,
            MongoAutoConfiguration.class, MongoDataAutoConfiguration.class))
        .withPropertyValues("spring.data.mongodb.uri=mongodb://localhost:27017/default_db",
            "spring.data.mongodb.dynamic.config.slave.uri=mongodb://localhost:27017/slave_db");

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DynamicMongoAutoConfiguration.class,
            MongoAutoConfiguration.class, MongoDataAutoConfiguration.class))
        .withPropertyValues("spring.data.mongodb.uri=mongodb://localhost:27017/default_db",
            "spring.data.mongodb.dynamic.config.slave.uri=mongodb://localhost:27017/slave_db");

    private final ReactiveWebApplicationContextRunner reactiveContextRunner = new ReactiveWebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DynamicMongoAutoConfiguration.class,
            MongoAutoConfiguration.class, MongoDataAutoConfiguration.class))
        .withPropertyValues("spring.data.mongodb.uri=mongodb://localhost:27017/default_db",
            "spring.data.mongodb.dynamic.config.slave.uri=mongodb://localhost:27017/slave_db");

    @Test
    void declaresAutoConfigurationOrderBeforeMongoDataAutoConfiguration() {
        AutoConfigureBefore declared =
            AnnotatedElementUtils.findMergedAnnotation(DynamicMongoAutoConfiguration.class, AutoConfigureBefore.class);

        assertThat(declared).as("必须显式声明 @AutoConfigureBefore").isNotNull();
        assertThat(Arrays.asList(declared.value())).contains(MongoDataAutoConfiguration.class);
    }

    @Test
    void dynamicDatabaseFactoryWinsOverBootsMongoDatabaseFactory() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MongoDatabaseFactory.class);
            assertThat(context.getBean(MongoDatabaseFactory.class)).isInstanceOf(DynamicMongoDatabaseFactory.class);
        });
    }

    /**
     * 非 Web 应用也必须装配出取值链：没有 DynamicContextValueParser Bean 时 Advisor 创建失败，应用启动直接报错。
     * 这一条同时锁住「非 Web 应用只给 SpEL」的行为。
     */
    @Test
    void providesValueParserOnNotWebApplication() {
        contextRunner.run(context -> assertThat(context.getBean(DynamicContextValueParser.class))
            .as("非 Web 应用应装配 SpEL 取值链")
            .isInstanceOf(SpElValueParser.class));
    }

    /**
     * Servlet Web 应用必须用 header → session → SpEL 链。
     * 注意：bean 条件在方法声明顺序里的先后决定谁胜出（SERVLET 分支必须排在 ANY 分支之前），
     * 本测试锁住该顺序，防止有人调整声明顺序后静默退化成只支持 SpEL。
     */
    @Test
    void providesServletValueParserOnServletWebApplication() {
        webContextRunner.run(context -> assertThat(context.getBean(DynamicContextValueParser.class))
            .as("Servlet Web 应用应从 header 取值链入口开始")
            .isInstanceOf(HeaderValueParser.class));
    }

    /**
     * spring.data.mongodb.dynamic.aop.enabled=false 时必须不注册 Advisor，否则等于没有开关。
     */
    @Test
    void disablesAnnotationAdvisorWhenAopDisabled() {
        contextRunner.withPropertyValues("spring.data.mongodb.dynamic.aop.enabled=false")
            .run(context -> assertThat(context)
                .as("aop.enabled=false 时不应注册 Advisor")
                .doesNotHaveBean(DynamicMongoAnnotationAdvisor.class));
    }

    /**
     * 未配置 aop.enabled 时默认要注册 Advisor（保持既有行为不变）。
     */
    @Test
    void keepsAnnotationAdvisorWhenAopEnabledByDefault() {
        contextRunner.run(context -> assertThat(context)
            .as("默认应注册 Advisor")
            .hasSingleBean(DynamicMongoAnnotationAdvisor.class));
    }

    /**
     * 用户自定义 DynamicMongoAnnotationAdvisor 时应整体替换掉我们的（与其它扩展点一致）。
     * 条件必须带类型：只写 @ConditionalOnMissingBean 会变成「容器里有任意 Advisor 就不注册我们的」，
     * 而 Boot 应用里出现别的 Advisor 是常态。
     */
    @Test
    void backsOffWhenUserDefinesOwnAdvisor() {
        contextRunner.withUserConfiguration(CustomAdvisorConfiguration.class)
            .run(context -> assertThat(context)
                .as("用户自带 Advisor 时我们应让位，不能出现两个同类 Bean")
                .hasSingleBean(DynamicMongoAnnotationAdvisor.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomAdvisorConfiguration {
        @Bean
        DynamicMongoAnnotationAdvisor customMongoAdvisor() {
            return new DynamicMongoAnnotationAdvisor(
                new DynamicMongoAnnotationInterceptor(new DynamicMongoClassResolver(true), null), DynamicMongo.class);
        }
    }

    /**
     * spring.data.mongodb.dynamic.enabled=false 时整个 starter 必须让位：不注册 Advisor，
     * 并且 MongoDatabaseFactory 回到 Boot 自己那份（否则「关掉」只关了一半）。
     */
    @Test
    void disablesEverythingWhenDynamicDisabled() {
        contextRunner.withPropertyValues("spring.data.mongodb.dynamic.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(DynamicMongoAnnotationAdvisor.class);
                assertThat(context).hasSingleBean(MongoDatabaseFactory.class);
                assertThat(context.getBean(MongoDatabaseFactory.class))
                    .as("关掉之后应由 Boot 的 MongoDatabaseFactory 接管")
                    .isNotInstanceOf(DynamicMongoDatabaseFactory.class);
            });
    }

    /**
     * Reactive Web 应用（WebFlux）走 ANY 分支：只有 SpEL 链，没有任何请求级取值能力。
     * 该 starter 不支持响应式切换（没有 ReactiveMongoDatabaseFactory），这一条把「装配不失败」
     * 锁住，避免以后调整条件时又出现「没有 parser Bean → 启动失败」。
     */
    @Test
    void providesSpElOnlyOnReactiveWebApplication() {
        reactiveContextRunner.run(context -> assertThat(context.getBean(DynamicContextValueParser.class))
            .as("Reactive Web 应用只有 SpEL 链")
            .isInstanceOf(SpElValueParser.class));
    }

    /**
     * 端到端：真实容器（含 Boot 的 AopAutoConfiguration → 代理创建器）+ 本 starter 的自动配置，
     * 带注解的 Bean 被调用时上下文必须被压栈。这条把「装配出来的切面真的生效」钉住——
     * 前面的测试只断言了 Bean 存在，没断言拦截真的发生。
     */
    @Test
    void interceptsAnnotatedBeanByDefault() {
        contextRunner.withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class))
            .withUserConfiguration(AnnotatedServiceConfiguration.class)
            .run(context -> {
                context.getBean(AnnotatedService.class).doWork();
                assertThat(AnnotatedService.OBSERVED).containsExactly("slave");
            });
    }

    /**
     * aop.enabled=false 时同一个 Bean 调用不应产生任何上下文（注解被忽略、不切库）。
     */
    @Test
    void doesNotInterceptWhenAopDisabled() {
        contextRunner.withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class))
            .withUserConfiguration(AnnotatedServiceConfiguration.class)
            .withPropertyValues("spring.data.mongodb.dynamic.aop.enabled=false")
            .run(context -> {
                context.getBean(AnnotatedService.class).doWork();
                assertThat(AnnotatedService.OBSERVED).containsExactly((String) null);
            });
    }

    @AfterEach
    void clearObservedContext() {
        AnnotatedService.OBSERVED.clear();
        DynamicMongoDatabaseContextHolder.clear();
    }

    static class AnnotatedService {
        static final List<String> OBSERVED = new ArrayList<>();

        @DynamicMongo("slave")
        public void doWork() {
            OBSERVED.add(DynamicMongoDatabaseContextHolder.peekInstanceId());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedServiceConfiguration {
        @Bean
        AnnotatedService annotatedService() {
            return new AnnotatedService();
        }
    }
}
