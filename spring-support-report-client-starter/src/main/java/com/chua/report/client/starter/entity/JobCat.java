package com.chua.report.client.starter.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Job日志查询请求
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Data
public class JobCat implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    private Integer logId;

    /**
     * 起始行号
     */
    private Integer fromLineNum;

    /**
     * 触发时间
     */
    private Date date;
}
