package com.chua.starter.datasource.spy;

import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.utils.StringUtils;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * appender
 * @author CH
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
     **/
    @Override
    public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared, String sql, String url) {
        if(StringUtils.isEmpty(sql)) {
            return "";
        }
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
