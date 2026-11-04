package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 链路 Span 的日志事件。
 *
 * @param time 毫秒戳
 * @param tags 标签
 * @author CH
 * @since 4.0.0.51
 */
public record TraceSpanLogVO(long time, List<TagVO> tags) {
}
