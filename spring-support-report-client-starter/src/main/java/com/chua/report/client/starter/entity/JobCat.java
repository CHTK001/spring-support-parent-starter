package com.chua.report.client.starter.entity;

import lombok.Data;

import java.util.Date;

/**
 * 任务分类
 * @author CH
 * @since 2024/9/15
 */
@Data
public class JobCat {

    /**
     * 任务ID
     */
    private long logId;

    /**
     * 分类ID
     */
    private Date date;

    /**
     * 分类名称
     */
    private int fromLineNum;
}
