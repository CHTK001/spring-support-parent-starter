package com.chua.starter.datasource.spy;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * P6Spy SQL 日志格式化器
 * <p>
 * 注意：此类在数据源初始化时被 P6Spy 加载，不能依赖任何可能延迟加载的类
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/10
 */
@Slf4j
public class SimpleAppender implements MessageFormattingStrategy {

    /**
     * 日志格式化方式（打印SQL日志会进入此方法，耗时操作，生产环境不建议使用）
     *
     * @param connectionId 连接ID
     * @param now          当前时间
     * @param elapsed      花费时间
     * @param category     类别
     * @param prepared     预编译SQL
     * @param sql          最终执行的SQL
     * @param url          数据库连接地址
     * @return 格式化日志结果
     * @author CH
     * @since 1.0.0
     */
    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared, String sql, String url) {
        // 使用内联判空，避免依赖外部 StringUtils（P6Spy 加载时外部类可能未加载）
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }
        String mapperMethod = MDC.get("mapper");
        String formattedSql = sql.replaceAll("[\\s]+", " ");
        if (mapperMethod != null && !mapperMethod.isEmpty()) {
            return " 耗时: " + elapsed + " ms " + now + "\n 执行的方法 ：" + mapperMethod + " \n 执行的SQL ：" + formattedSql + "\n";
        }
        return " 耗时: " + elapsed + " ms " + now + "\n 执行的SQL：" + formattedSql + "\n";
    }
}
