package com.chua.starter.collect.engine;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.reflection.ReflectUtils.MethodDescriptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内置函数调用器：表达式语言与脚本 {@code fn} 共用的唯一一条反射通道。
 *
 * <p>函数表只收录 {@link CollectFunctions} 的公开静态方法，按名字单值登记，与
 * {@code ${...}} 求值器历史上的行为完全一致。名字未命中内置表时交给
 * {@link UserHook}，由自定义函数库决定命中与否，因此 {@code ${sign(x)}} 与
 * {@code fn.sign(x)} 看到的是同一批函数。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectBuiltinInvoker {

    /**
     * 内置函数表，仅收录 CollectFunctions 的公开静态方法。
     */
    private static final Map<String, MethodDescriptor> FUNCTIONS = new HashMap<>();

    /**
     * 自定义函数回调，未命中时抛「不支持的函数」。
     */
    private static volatile UserHook userHook;

    static {
        for (MethodDescriptor method : ReflectUtils.publicStaticMethods(CollectFunctions.class)) {
            FUNCTIONS.putIfAbsent(method.name(), method);
        }
    }

    private CollectBuiltinInvoker() {
    }

    /**
     * 内置函数名清单，用于校验用户函数是否重名。
     *
     * @return 函数名集合
     */
    public static Set<String> builtinNames() {
        return Set.copyOf(FUNCTIONS.keySet());
    }

    /**
     * 名字是否为内置函数。
     *
     * @param name 函数名
     * @return 命中返回 true
     */
    public static boolean isBuiltin(String name) {
        return FUNCTIONS.containsKey(name);
    }

    /**
     * 登记自定义函数回调，由函数库注册表在装配时调用。
     *
     * @param hook 回调，传 null 表示摘除
     */
    public static void registerUserHook(UserHook hook) {
        userHook = hook;
    }

    /**
     * 调用函数：先查内置表，未命中走自定义函数库。
     *
     * @param name      函数名
     * @param arguments 实参
     * @return 调用结果
     */
    public static Object invoke(String name, List<Object> arguments) {
        MethodDescriptor method = FUNCTIONS.get(name);
        if (method == null) {
            return invokeUser(name, arguments);
        }
        Object[] args = method.parameterTypes().length == 0
                ? new Object[0] : coerceArguments(method, arguments);
        try {
            return ReflectUtils.invokeStaticStrict(CollectFunctions.class, method, args);
        } catch (Throwable ex) {
            throw new IllegalArgumentException("函数 " + name + " 执行失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 交给自定义函数库，未登记回调或函数库没有这个名字时给出与内置一致的报错。
     *
     * @param name      函数名
     * @param arguments 实参
     * @return 调用结果
     */
    private static Object invokeUser(String name, List<Object> arguments) {
        UserHook hook = userHook;
        if (hook != null) {
            Outcome outcome = hook.call(name, arguments);
            if (outcome.hit()) {
                return outcome.value();
            }
        }
        throw new IllegalArgumentException("不支持的函数: " + name);
    }

    /**
     * 按形参类型转换实参，支持 Object 形参直通与数字宽度转换。
     *
     * @param method    白名单目标方法描述
     * @param arguments 实参
     * @return 转换后的参数数组
     */
    private static Object[] coerceArguments(MethodDescriptor method, List<Object> arguments) {
        Class<?>[] types = method.parameterTypes();
        Object[] args = new Object[types.length];
        for (int index = 0; index < types.length; index++) {
            Object value = index < arguments.size() ? arguments.get(index) : null;
            if (value == null) {
                args[index] = null;
                continue;
            }
            Class<?> type = types[index];
            if (type.isInstance(value)) {
                args[index] = value;
            } else if (type == String.class) {
                args[index] = CollectFunctions.stringify(value);
            } else if (Number.class.isAssignableFrom(boxed(type))) {
                args[index] = numeric(value, type);
            } else if (type == boolean.class || type == Boolean.class) {
                args[index] = CollectExpression.truthy(value);
            } else if (type == List.class) {
                args[index] = value instanceof Collection ? new ArrayList<>((Collection<?>) value)
                        : List.of(String.valueOf(value).split(","));
            } else {
                args[index] = value;
            }
        }
        return args;
    }

    /**
     * 基本类型装箱。
     *
     * @param type 形参类型
     * @return 装箱类型
     */
    private static Class<?> boxed(Class<?> type) {
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        return type;
    }

    /**
     * 数字目标类型转换。
     *
     * @param value 原值
     * @param type  目标类型
     * @return 转换结果
     */
    private static Object numeric(Object value, Class<?> type) {
        double point = Double.parseDouble(String.valueOf(value).trim());
        if (type == int.class || type == Integer.class) {
            return (int) point;
        }
        if (type == long.class || type == Long.class) {
            return (long) point;
        }
        return point;
    }

    /**
     * 自定义函数回调。
     */
    @FunctionalInterface
    public interface UserHook {

        /**
         * 尝试调用自定义函数。
         *
         * @param name      函数名
         * @param arguments 实参
         * @return 命中与否的结果，执行失败直接抛异常
         */
        Outcome call(String name, List<Object> arguments);
    }

    /**
     * 回调结果，{@code hit=false} 表示函数库里没有这个名字。
     *
     * @param hit   是否命中
     * @param value 返回值
     */
    public record Outcome(boolean hit, Object value) {

        /**
         * 未命中哨兵。
         */
        public static final Outcome MISS = new Outcome(false, null);

        /**
         * 构造命中结果。
         *
         * @param value 返回值
         * @return 命中结果
         */
        public static Outcome of(Object value) {
            return new Outcome(true, value);
        }
    }
}
