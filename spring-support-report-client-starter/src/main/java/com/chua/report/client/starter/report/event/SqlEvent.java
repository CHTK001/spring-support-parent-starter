package com.chua.report.client.starter.report.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * sql事件
 * @author CH
 * @since 2024/9/7
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "SQL事件")
public class SqlEvent extends TimestampEvent implements Serializable {

    /**
     * sql
     */
    private String sql;

    /**
     * 线程
     */
    private String thread;

    /**
     * 事件
     */
    private String event;
    /**
     * 类名
     */
    private String className;


}
