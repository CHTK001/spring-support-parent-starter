package com.chua.starter.mybatis.p6spy;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * mybatis p6spy日志
 *
 * @author CH
 * @since 2025/6/27 15:51
 */
public class MybatisP6SpyLogger implements MessageFormattingStrategy {
    private static final ThreadLocal<Deque<String>> methodStack = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 压栈
     *
     * @param method 方法
     */
    public static void pushMethod(String method) {
        methodStack.get().push(method);
    }

    /**
     * 弹栈
     */
    public static void popMethod() {
        Deque<String> stack = methodStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared, String sql, String url) {
        if (StringUtils.isNotBlank(sql)) {
            String currentMethod = methodStack.get().peek();
            if (null != currentMethod) {
                return " 耗时: " + elapsed + " ms " + now + "\n 方法：" + currentMethod + " 执行的SQL：" + sql.replaceAll("[\\s]+", " ") + "\n";
            }
            return " 耗时: " + elapsed + " ms " + now + "\n 执行的SQL：" + sql.replaceAll("[\\s]+", " ") + "\n";
        }
        return "";
    }
}
