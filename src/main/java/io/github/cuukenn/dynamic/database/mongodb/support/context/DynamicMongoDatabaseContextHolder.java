
package io.github.cuukenn.dynamic.database.mongodb.support.context;

import io.github.cuukenn.dynamic.database.mongodb.support.DynamicMongoContext;
import org.springframework.core.NamedThreadLocal;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * 核心基于ThreadLocal的切换数据源工具类
 *
 * @author TaoYu Kanyuxia
 * @since 1.0.0
 */
public final class DynamicMongoDatabaseContextHolder {

    /**
     * 为什么要用链表存储(准确的是栈)
     * <pre>
     * 为了支持嵌套切换，如ABC三个service都是不同的数据源
     * 其中A的某个业务要调B的方法，B的方法需要调用C的方法。一级一级调用切换，形成了链。
     * 传统的只设置当前线程的方式不能满足此业务需求，必须使用栈，后进先出。
     * </pre>
     */
    private static final ThreadLocal<Deque<DynamicMongoContext>> LOOKUP_KEY_HOLDER = new NamedThreadLocal<Deque<DynamicMongoContext>>("dynamic-mongo-database") {
        @Override
        protected Deque<DynamicMongoContext> initialValue() {
            return new ArrayDeque<>();
        }
    };

    private DynamicMongoDatabaseContextHolder() {
    }

    /**
     * 获得当前线程数据源
     *
     * @return 数据源名称
     */
    public static DynamicMongoContext peek() {
        return LOOKUP_KEY_HOLDER.get().peek();
    }

    /**
     * 栈里最近一个非空的 instanceId。
     * <p>
     * 注解里没写 instanceId 表示「继承外层上下文」而不是「回落默认源」：例如外层方法切到 slave，
     * 内层方法只声明了 databaseName，那么内层调用仍然应该用 slave 的客户端。
     * </p>
     *
     * @return 最近的非空 instanceId，栈里没有则返回 null
     */
    public static String peekInstanceId() {
        return peekFirst(DynamicMongoContext::getInstanceId);
    }

    /**
     * 栈里最近一个非空的 database（语义同 {@link #peekInstanceId()}）。
     *
     * @return 最近的非空库名，栈里没有则返回 null
     */
    public static String peekDatabase() {
        return peekFirst(DynamicMongoContext::getDatabase);
    }

    private static String peekFirst(Function<DynamicMongoContext, String> getter) {
        for (DynamicMongoContext context : LOOKUP_KEY_HOLDER.get()) {
            String value = getter.apply(context);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 设置当前线程数据源
     * <p>
     * 如非必要不要手动调用，调用后确保最终清除
     * </p>
     *
     * @param context 数据源名称
     */
    public static DynamicMongoContext push(DynamicMongoContext context) {
        if (context == null) {
            context = new DefaultDynamicMongoContext();
        }
        LOOKUP_KEY_HOLDER.get().push(context);
        return context;
    }

    /**
     * 清空当前线程数据源
     * <p>
     * 如果当前线程是连续切换数据源 只会移除掉当前线程的数据源名称
     * </p>
     */
    public static void poll() {
        Deque<DynamicMongoContext> deque = LOOKUP_KEY_HOLDER.get();
        deque.poll();
        if (deque.isEmpty()) {
            LOOKUP_KEY_HOLDER.remove();
        }
    }

    /**
     * 当前线程上下文栈的快照（顺序为栈顶→栈底，即「最内层→最外层」），交给别的线程时可原样 {@link #restore(java.util.List)}。
     * <p>
     * 用途：把上下文手工传递到别的线程（例如包一层 {@code TaskDecorator}），因为本 holder 是 ThreadLocal，
     * 不跨线程。
     * </p>
     *
     * @return 不可变快照，当前线程没有上下文时返回空列表
     */
    public static List<DynamicMongoContext> snapshot() {
        Deque<DynamicMongoContext> deque = LOOKUP_KEY_HOLDER.get();
        if (deque.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(deque));
    }

    /**
     * 用快照覆盖当前线程的上下文栈（传空列表等于清空）。
     *
     * @param contexts {@link #snapshot()} 的结果，或其它同序列表
     */
    public static void restore(List<DynamicMongoContext> contexts) {
        Deque<DynamicMongoContext> deque = LOOKUP_KEY_HOLDER.get();
        deque.clear();
        if (contexts == null || contexts.isEmpty()) {
            LOOKUP_KEY_HOLDER.remove();
            return;
        }
        //push 是头插：倒着压才能让列表第一个元素位于栈顶
        for (int i = contexts.size() - 1; i >= 0; i--) {
            deque.push(contexts.get(i));
        }
    }

    /**
     * 强制清空本地线程
     * <p>
     * 防止内存泄漏，如手动调用了push可调用此方法确保清除
     * </p>
     */
    public static void clear() {
        LOOKUP_KEY_HOLDER.remove();
    }
}
