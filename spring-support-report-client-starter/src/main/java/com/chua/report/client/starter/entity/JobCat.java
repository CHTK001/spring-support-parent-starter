package com.chua.report.client.starter.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
* 作业日志查询请求
*
* @版本 1.0.0
* @since 2024/12/06
* @author CH
 */
@Data
public class JobCat implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 日志标识
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
