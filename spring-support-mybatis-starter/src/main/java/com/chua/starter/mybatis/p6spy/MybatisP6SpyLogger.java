package com.chua.starter.mybatis.p6spy;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.slf4j.MDC;

/**
 * mybatis p6spy日志
 *
 * @author CH
 * @since 2025/6/27 15:51
 */
public class MybatisP6SpyLogger implements MessageFormattingStrategy {

    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared, String sql, String url) {
        if (StringUtils.isNotBlank(sql)) {
            String mapperMethod = MDC.get("mapper");
            if (null != mapperMethod) {
                return " 耗时: " + elapsed + " ms " + now + "\n 执行的方法 ：" + mapperMethod + " \n 执行的SQL ：" + sql.replaceAll("[\\s]+", " ") + "\n";
            }
            return " 耗时: " + elapsed + " ms " + now + "\n 执行的SQL：" + sql.replaceAll("[\\s]+", " ") + "\n";
        }
        return "";
    }
}
