package io.github.cuukenn.dynamic.database.mongodb.support.context;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import org.springframework.core.task.TaskDecorator;

import java.util.List;

/**
 * 把当前线程的动态数据源上下文传递给被提交的任务（{@code @Async} / 线程池）。
 * <p>
 * 上下文存在 ThreadLocal 里，本来不跨线程：外层方法 push 完就把任务提交出去、立刻 poll，
 * 任务在线程池里跑的时候栈已经空了，于是静默走默认源。这个 decorator 在**提交任务的线程**里
 * 抓快照，在**执行任务的线程**里装回快照、跑完再还原该线程原有的上下文。
 * </p>
 * <p>
 * 用法（按需声明，starter 不会自动注册——是否让上下文跟随任务属于应用的策略）：
 * <pre>
 * &#64;Bean
 * public TaskDecorator dynamicMongoContextTaskDecorator() {
 *     return new DynamicMongoContextTaskDecorator();
 * }
 * </pre>
 * Spring Boot 的 {@code ThreadPoolTaskExecutor} 自动配置会把这个 {@code TaskDecorator} Bean 应用到线程池上。
 * </p>
 *
 * @author changgg
 */
public class DynamicMongoContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        final List<DynamicMongoContext> captured = DynamicMongoDatabaseContextHolder.snapshot();
        if (captured.isEmpty()) {
            //提交时本来就没有上下文：不要包装，否则会把执行线程自己的上下文清掉
            return runnable;
        }
        return () -> {
            final List<DynamicMongoContext> previous = DynamicMongoDatabaseContextHolder.snapshot();
            DynamicMongoDatabaseContextHolder.restore(captured);
            try {
                runnable.run();
            } finally {
                //线程池会复用线程：必须还原成任务执行前的样子
                DynamicMongoDatabaseContextHolder.restore(previous);
            }
        };
    }
}
