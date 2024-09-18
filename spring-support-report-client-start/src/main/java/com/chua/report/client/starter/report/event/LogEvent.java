package com.chua.report.client.starter.report.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * log事件
 * @author CH
 * @since 2024/9/7
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "事件")
public class LogEvent extends TimestampEvent implements Serializable {

    /**
     * 日志级别
     */
    @Schema(description = "日志级别")
    private String level;
    /**
     * traceId
     */
    @Schema(description = "traceId")
    private String traceId;
    /**
     * 日志内容
     */
    @Schema(description = "日志内容")
    private String message;
    /**
     * 日志类
     */
    @Schema(description = "日志类")
    private String logger;
    /**
     * 线程
     */
    @Schema(description = "线程")
    private String thread;
    /**
     * 类名
     */
    @Schema(description = "类名")
    private String className;
    /**
     * 行号
     */
    @Schema(description = "行号")
    private Integer line;

}
