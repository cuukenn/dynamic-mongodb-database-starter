package io.github.cuukenn.dynamic.database.mongodb.support.context;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 上下文跨线程传播（{@code TaskDecorator}）。
 * <p>
 * 默认场景下 {@code @Async}/线程池会丢掉上下文并静默走默认源：外层方法 push 完提交任务、立刻 poll，
 * 任务在线程池里跑时栈已空。这个 decorator 是显式的补丁，测试要锁住三件事：
 * 传进去、跑完还原 worker 原来的上下文、提交时没有上下文就不包装（不能污染 worker）。
 */
class DynamicMongoContextTaskDecoratorTest {

    private final DynamicMongoContextTaskDecorator decorator = new DynamicMongoContextTaskDecorator();

    @AfterEach
    void cleanup() {
        DynamicMongoDatabaseContextHolder.clear();
    }

    /**
     * 在另一个线程里执行任务，线程里看到的上下文必须和提交时一致。
     */
    @Test
    void propagatesContextIntoAnotherThread() throws Exception {
        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("slave", "slave_db"));

        AtomicReference<String> seenInWorker = new AtomicReference<>();
        AtomicReference<String> seenInSubmitter = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> seenInWorker.set(DynamicMongoDatabaseContextHolder.peekInstanceId()));

        //模拟「提交完立刻弹栈」——这正是 @Async 场景下上下文丢失的原因
        DynamicMongoDatabaseContextHolder.poll();
        seenInSubmitter.set(DynamicMongoDatabaseContextHolder.peekInstanceId());

        runInAnotherThread(decorated);

        assertNull(seenInSubmitter.get(), "提交方自己已经弹栈");
        assertEquals("slave", seenInWorker.get(), "任务线程必须拿得到提交时的上下文");
    }

    /**
     * worker 线程上原有的上下文不能被任务污染：任务跑完要还原成执行前的样子。
     */
    @Test
    void restoresWorkerThreadsOwnContextAfterTask() throws Exception {
        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("report", "report_db"));

        AtomicReference<String> insideTask = new AtomicReference<>();
        AtomicReference<String> afterTask = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> insideTask.set(DynamicMongoDatabaseContextHolder.peekInstanceId()));

        Thread worker = new Thread(() -> {
            DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("worker_own", "worker_db"));
            try {
                decorated.run();
                afterTask.set(DynamicMongoDatabaseContextHolder.peekInstanceId());
            } finally {
                DynamicMongoDatabaseContextHolder.clear();
            }
        });
        worker.start();
        worker.join();

        assertEquals("report", insideTask.get());
        assertEquals("worker_own", afterTask.get(), "任务结束后必须还原 worker 线程自己的上下文");
    }

    /**
     * 嵌套栈也要完整传递（栈顶是最内层），并且顺序不能反。
     */
    @Test
    void propagatesWholeNestedStackInOrder() throws Exception {
        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("outer", "outer_db"));
        DynamicMongoDatabaseContextHolder.push(new DefaultDynamicMongoContext("inner", "inner_db"));

        AtomicReference<String> innermost = new AtomicReference<>();
        AtomicReference<String> deepest = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            Deque<DynamicMongoContext> stack = new ArrayDeque<>(DynamicMongoDatabaseContextHolder.snapshot());
            innermost.set(DynamicMongoDatabaseContextHolder.peekInstanceId());
            deepest.set(stack.peekLast().getInstanceId());
        });

        runInAnotherThread(decorated);

        assertEquals("inner", innermost.get(), "栈顶必须还是最内层");
        assertEquals("outer", deepest.get(), "栈底必须还是最外层");
    }

    /**
     * 提交时没有上下文 → 不包装：否则会把 worker 线程自己的上下文清掉。
     */
    @Test
    void leavesTaskUnwrappedWhenNoContextAtSubmitTime() {
        Runnable task = () -> {
        };

        assertSame(task, decorator.decorate(task), "没有上下文时不应包装任务");
    }

    private static void runInAnotherThread(Runnable runnable) throws InterruptedException {
        Thread worker = new Thread(runnable);
        worker.start();
        worker.join();
    }
}
