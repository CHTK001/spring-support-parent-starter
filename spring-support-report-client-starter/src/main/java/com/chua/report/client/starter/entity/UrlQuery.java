package com.chua.report.client.starter.entity;

import lombok.Data;

import java.util.Date;

/**
 * url参数
 *
 * @author CH
 * @since 2025/8/14 20:38
 */
@Data
public class UrlQuery {

    /**
     * 开始时间
     * 例如：2025-08-14 20:00:00
     */
    private Date startTime;
    
    /**
     * 结束时间
     * 例如：2025-08-14 21:00:00
     */
    private Date endTime;
    
    /**
     * 过滤类型
     * 例如："login", "system"
     */
    private String filterType;
    
    /**
     * 过滤查询条件
     * 例如："userId=123", "status=active"
     */
    private String filterQuery;
}
