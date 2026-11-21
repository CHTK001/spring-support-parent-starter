package com.chua.starter.logger.store;

import com.chua.starter.logger.event.LoggerEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 日志查询分页结果；{@code cursor} 为空串表示没有更多数据。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoggerPage {

    /**
     * 当前页事件
     */
    private List<LoggerEvent> items;

    /**
     * 下一页游标；空串表示无更多
     */
    private String cursor;
}
