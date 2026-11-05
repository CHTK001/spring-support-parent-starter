package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 链路列表与分页计数。
 *
 * @param list 链路摘要
 * @param total  总线路数
 * @param page   当前页码（从 1 开始）
 * @param size   每页条数
 * @author CH
 * @since 4.0.0.51
 */
public record SkywalkingTracePageVO(List<TraceSummaryVO> list,
                                    long total,
                                    int page,
                                    int size) {
}
