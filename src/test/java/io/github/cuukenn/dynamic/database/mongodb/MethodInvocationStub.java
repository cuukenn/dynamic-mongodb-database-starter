package io.github.cuukenn.dynamic.database.mongodb;

import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

/**
 * 测试用 {@link MethodInvocation} 桩：取值链/上下文只用得到 method、arguments、this 三样东西。
 */
public final class MethodInvocationStub implements MethodInvocation {
    private final Method method;
    private final Object target;
    private final Object[] arguments;

    private MethodInvocationStub(Method method, Object target, Object[] arguments) {
        this.method = method;
        this.target = target;
        this.arguments = arguments;
    }

    public static MethodInvocationStub of(Object target, String methodName, Object... arguments) {
        try {
            return new MethodInvocationStub(target.getClass().getMethod(methodName, parameterTypes(arguments)), target, arguments);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("no method " + methodName + " on " + target.getClass(), e);
        }
    }

    private static Class<?>[] parameterTypes(Object[] arguments) {
        Class<?>[] types = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            types[i] = arguments[i].getClass();
        }
        return types;
    }

    @Override
    public Object proceed() {
        return null;
    }

    @Override
    public Object getThis() {
        return target;
    }

    @Override
    public AccessibleObject getStaticPart() {
        return method;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Object[] getArguments() {
        return arguments;
    }
}
