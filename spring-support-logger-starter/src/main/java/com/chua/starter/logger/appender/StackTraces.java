package com.chua.starter.logger.appender;

/**
 * 堆栈文本工具：桥接 Appender 共用（不依赖任何具体日志实现类型，
 * 便于 Logback / Log4j2 桥接类在各自绑定缺席时独立加载）。
 *
 * @author CH
 * @since 4.0.0.51
 */
final class StackTraces {

    /**
     * 堆栈文本上限（字符）
     */
    static final int MAX_STACKTRACE = 8192;

    private StackTraces() {
    }

    /**
     * 截断堆栈文本
     *
     * @param text 原文
     * @return 截断后的文本
     */
    static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_STACKTRACE ? text : text.substring(0, MAX_STACKTRACE);
    }

    /**
     * 异常转堆栈文本（含 Cause 链）
     *
     * @param throwable 异常
     * @return 堆栈文本
     */
    static String stacktraceOf(Throwable throwable) {
        java.io.StringWriter writer = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(writer));
        return truncate(writer.toString());
    }
}
