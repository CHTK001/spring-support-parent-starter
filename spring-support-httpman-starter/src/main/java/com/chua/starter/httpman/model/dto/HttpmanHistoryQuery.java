package com.chua.starter.httpman.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口调试控制台 - 历史查询参数
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
public class HttpmanHistoryQuery {

    /**
     * 请求标识，可空
     */
    private String requestId;

    /**
     * 集合标识，可空
     */
    private String collectionId;

    /**
     * 开始时间，可空
     */
    private LocalDateTime startTime;

    /**
     * 结束时间，可空
     */
    private LocalDateTime endTime;

    /**
     * 按响应状态码筛选，0 表示不筛选
     */
    private Integer status;

    /**
     * 页码，从 1 开始
     */
    private long pageNo = 1;

    /**
     * 每页条数，默认 20
     */
    // [P3C A20 豁免]
    private long pageSize = 20;
}
