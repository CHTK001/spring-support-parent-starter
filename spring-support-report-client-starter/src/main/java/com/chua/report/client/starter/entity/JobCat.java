package com.chua.report.client.starter.entity;

import java.io.Serializable;
import java.util.Date;

/**
 * 作业日志查询条件。
 *
 * @param logId       日志标识
 * @param fromLineNum 起始行号
 * @param date        查询时间
 * @since 2024/12/06
 * @author CH
 */
public record JobCat(
        Integer logId,
        Integer fromLineNum,
        Date date) implements Serializable {

    private static final long serialVersionUID = 1L;
}
