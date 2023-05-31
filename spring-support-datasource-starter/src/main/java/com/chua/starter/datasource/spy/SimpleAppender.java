package com.chua.starter.datasource.spy;

import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.lang.formatter.HighlightingFormatter;
import com.chua.common.support.lang.formatter.SqlFormatter;
import com.chua.common.support.log.Log;
import com.chua.common.support.utils.StringUtils;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

/**
 * appender
 * @author CH
 */
public class SimpleAppender implements MessageFormattingStrategy {

    private static final Log log = Log.getLogger(MessageFormattingStrategy.class);

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
        log.info("数据库链接ID{}", connectionId);
        log.info("查询耗时{}", elapsed);
        if(log.isDebugEnabled()) {
            log.debug("查询时间: {}", DateTime.of(now).toStandard());
            log.debug("数据库连接地址: {}", url);
        }
        log.info("查询SQL \r\n {}", HighlightingFormatter.INSTANCE.format(SqlFormatter.format(sql)));
        return "";
    }
}
