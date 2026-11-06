package com.chua.starter.collect.engine;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 脚本节点运行时：把流程变量交给 Groovy 求值。
 *
 * <p>整个模块里只有本类的内部持有者引用 {@code groovy.*}，且只在依赖齐备时才加载，
 * 因此没有引入 groovy 的宿主仍然能正常装配脚本节点——执行时给出可读提示而不是启动失败。</p>
 *
 * <p>脚本作者拥有完整的 JVM 代码执行能力（与 spider-flow 的脚本节点同等定位），
 * 能编排流程即等价于能执行任意代码，边界是控制台会话认证；{@code plugin.collect.script.enable=false}
 * 可以整体关掉这个节点类型。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectGroovyRuntime {

    /**
     * 编译缓存上限，超过后整体清空，避免长时间运行的实例累积历史脚本。
     */
    private static final int CACHE_LIMIT = 64;

    /**
     * 脚本源码到编译结果。
     */
    private static final Map<String, Class<?>> COMPILED = new LinkedHashMap<>();

    /**
     * 依赖探测结果，进程内不变。
     */
    private static final boolean AVAILABLE = probe();

    private CollectGroovyRuntime() {
    }

    /**
     * 当前类路径是否有 Groovy。
     *
     * @return 可用返回 true
     */
    public static boolean available() {
        return AVAILABLE;
    }

    /**
     * 执行一段脚本。
     *
     * @param script     脚本源码
     * @param bindings   绑定变量，脚本里按下划线作用域直接引用
     * @param timeoutMs  最长执行毫秒，超时后中断脚本线程
     * @return 脚本最后一个表达式的值
     * @throws Exception 脚本编译失败、执行抛错或超时
     */
    public static Object evaluate(String script, Map<String, Object> bindings, long timeoutMs) throws Exception {
        if (!AVAILABLE) {
            throw new IllegalStateException("脚本节点需要 org.apache.groovy:groovy 依赖，当前类路径没有该 jar");
        }
        long limit = timeoutMs <= 0 ? 30_000L : timeoutMs;
        AtomicReference<Object> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = Thread.ofVirtual().name("collect-script-").unstarted(() -> {
            try {
                value.set(Holder.run(script, bindings));
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        worker.start();
        worker.join(limit);
        if (worker.isAlive()) {
            worker.interrupt();
            worker.join(1000L);
            throw new IllegalStateException("脚本执行超过 " + limit + "ms，已中断");
        }
        if (failure.get() != null) {
            throw new IllegalStateException("脚本执行失败: " + describe(failure.get()), failure.get());
        }
        return value.get();
    }

    /**
     * 编译检查一段脚本，不执行。
     *
     * @param script 脚本源码
     * @return 编译通过返回 null，否则返回单行错误说明
     */
    public static String check(String script) {
        if (!AVAILABLE) {
            return "脚本节点需要 org.apache.groovy:groovy 依赖，当前类路径没有该 jar";
        }
        try {
            Holder.compile(script);
            return null;
        } catch (Throwable ex) {
            return describe(ex);
        }
    }

    /**
     * 执行一段闭包源码。
     *
     * <p>自定义函数登记的就是 {@code { a, b -> ... }} 形态的闭包；每次调用都换一份绑定，
     * 因此函数体里读到 {@code fn} 时指向本次调用的函数分发器。</p>
     *
     * @param script   闭包源码
     * @param bindings 绑定变量
     * @param args     位置实参
     * @return 闭包返回值
     * @throws IllegalStateException 编译或执行失败
     */
    public static Object callClosure(String script, Map<String, Object> bindings, Object[] args) {
        if (!AVAILABLE) {
            throw new IllegalStateException("自定义函数需要 org.apache.groovy:groovy 依赖，当前类路径没有该 jar");
        }
        try {
            return Holder.callClosure(script, bindings, args);
        } catch (Exception ex) {
            throw new IllegalStateException("函数执行失败: " + describe(ex), ex);
        }
    }

    /**
     * 取闭包字面量的形参个数。
     *
     * <p>只求值闭包本身，不会执行函数体，因此登记函数时可以直接问界面该传几个参数。</p>
     *
     * @param script 闭包源码
     * @return 形参个数，源码不是闭包字面量返回 -1
     * @throws IllegalStateException 依赖缺失或编译失败
     */
    public static int closureParameters(String script) {
        if (!AVAILABLE) {
            throw new IllegalStateException("自定义函数需要 org.apache.groovy:groovy 依赖，当前类路径没有该 jar");
        }
        try {
            Object value = Holder.run(script, Map.of());
            return value instanceof groovy.lang.Closure<?> closure
                    ? closure.getMaximumNumberOfParameters() : -1;
        } catch (Exception ex) {
            throw new IllegalStateException("函数编译失败: " + describe(ex), ex);
        }
    }

    /**
     * 构造脚本里的 {@code fn} 对象。
     *
     * <p>它不声明任何方法，所有调用都落到 {@link CollectBuiltinInvoker}，于是
     * {@code fn.md5(x)}、{@code fn.自定义(x)} 与表达式里的 {@code ${md5(x)}} 走同一张表。</p>
     *
     * @return 可放进脚本绑定的函数分发对象
     */
    public static Object functionObject() {
        if (!AVAILABLE) {
            // 没有 groovy 时退回原始静态类绑定，让 evaluate 给出缺依赖的可读提示，而不是链接期炸。
            return CollectFunctions.class;
        }
        return new FunctionObject((name, arguments) ->
                CollectBuiltinInvoker.invoke(name, Arrays.asList(arguments)));
    }

    /**
     * 取异常的第一行说明，脚本栈通常几十行且都是 Groovy 内部帧。
     *
     * @param error 异常
     * @return 单行说明
     */
    private static String describe(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        int line = message.indexOf('\n');
        return line < 0 ? message : message.substring(0, line);
    }

    /**
     * 探测依赖，只探测不初始化，避免无 groovy 时报类解析错误。
     *
     * @return 可用返回 true
     */
    private static boolean probe() {
        try {
            Class.forName("groovy.lang.GroovyShell", false, CollectGroovyRuntime.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 函数分发回调。
     */
    public interface FunctionDispatcher {

        /**
         * 按名字调用一个函数。
         *
         * @param name      函数名
         * @param arguments 实参
         * @return 调用结果
         */
        Object dispatch(String name, Object[] arguments);
    }

    /**
     * 脚本里的 {@code fn}，把所有未知方法转发给分发器。
     */
    private static final class FunctionObject extends groovy.lang.GroovyObjectSupport {

        /**
         * 转发目标。
         */
        private final FunctionDispatcher dispatcher;

        private FunctionObject(FunctionDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public Object invokeMethod(String name, Object arguments) {
            return dispatcher.dispatch(name, args(arguments));
        }

        /**
         * Groovy 传单值时不装箱成数组，统一成位置实参数组。
         *
         * @param arguments 原始实参
         * @return 位置实参数组
         */
        private static Object[] args(Object arguments) {
            if (arguments instanceof Object[] array) {
                return array;
            }
            return arguments == null ? new Object[0] : new Object[]{arguments};
        }
    }

    /**
     * 编译并运行脚本。与 {@link #evaluate} 分开，保证缺依赖时该类不会被链接。
     */
    private static final class Holder {

        private Holder() {
        }

        /**
         * 编译（命中缓存则复用）并执行一次脚本。
         *
         * @param script   脚本源码
         * @param bindings 绑定变量
         * @return 脚本返回值
         * @throws Exception 编译或执行失败
         */
        static Object run(String script, Map<String, Object> bindings) throws Exception {
            groovy.lang.Script instance = instantiate(script, bindings);
            return instance.run();
        }

        /**
         * 只编译，用于保存前的语法检查。
         *
         * @param script 脚本源码
         * @throws Exception 编译失败
         */
        static void compile(String script) throws Exception {
            compiled(script);
        }

        /**
         * 把脚本当闭包字面量求值并按位置实参调用一次。
         *
         * @param script   闭包源码
         * @param bindings 绑定变量
         * @param args     位置实参
         * @return 闭包返回值
         * @throws Exception 编译或执行失败
         */
        static Object callClosure(String script, Map<String, Object> bindings, Object[] args) throws Exception {
            Object value = instantiate(script, bindings).run();
            if (!(value instanceof groovy.lang.Closure<?> closure)) {
                throw new IllegalStateException("函数体必须是 { a, b -> ... } 形态的闭包");
            }
            return closure.call(args);
        }

        /**
         * 用给定绑定实例化一个脚本对象。
         *
         * @param script   脚本源码
         * @param bindings 绑定变量
         * @return 脚本实例
         * @throws Exception 编译或实例化失败
         */
        private static groovy.lang.Script instantiate(String script, Map<String, Object> bindings) throws Exception {
            groovy.lang.Script instance = (groovy.lang.Script) compiled(script).getDeclaredConstructor().newInstance();
            instance.setBinding(new groovy.lang.Binding(new LinkedHashMap<>(bindings)));
            return instance;
        }

        /**
         * 取编译后的脚本类，命中缓存则复用。
         *
         * @param script 脚本源码
         * @return 脚本类
         */
        private static Class<?> compiled(String script) {
            synchronized (COMPILED) {
                if (COMPILED.size() > CACHE_LIMIT) {
                    COMPILED.clear();
                }
                Class<?> cached = COMPILED.get(script);
                if (cached != null) {
                    return cached;
                }
                groovy.lang.GroovyShell shell = new groovy.lang.GroovyShell(new groovy.lang.Binding());
                Class<?> compiled = shell.getClassLoader().parseClass(script);
                COMPILED.put(script, compiled);
                return compiled;
            }
        }
    }
}
