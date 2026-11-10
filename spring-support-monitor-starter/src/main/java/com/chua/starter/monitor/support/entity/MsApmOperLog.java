package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * APM 操作日志（一行 = 一次 APM 写操作）。
 *
 * <p>与 {@code @SysLog} 那条链路的区别：APM 接口不走 Spring MVC（嵌入模式是独立
 * Servlet、独立模式自起 HTTP 服务），切面拦不到它，所以这里没有监听器，
 * 由 {@code ApmDispatcher} 在写操作完成后直接回调 {@code ApmOperLogSink} 落库。</p>
 *
 * <p>只记写操作：GET 是查询，每次打开页面都留一条会把时间线淹掉，
 * 也丢掉了「谁改了设置」这个日志唯一的语义。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "APM 操作日志")
@TableName("ms_apm_oper_log")
public class MsApmOperLog implements Serializable {

    /**
     * 串行版本 UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 操作码
     */
    @TableField("action")
    @Schema(description = "操作码", example = "CONFIG_UPDATE")
    private String action;

    /**
     * 操作中文名
     */
    @TableField("action_name")
    @Schema(description = "操作中文名", example = "保存 APM 设置")
    private String actionName;

    /**
     * 操作对象（本次改动的设置项）
     */
    @TableField("target")
    @Schema(description = "操作对象", example = "enabled,type,oapUrl")
    private String target;

    /**
     * 补充说明
     */
    @TableField("detail")
    @Schema(description = "补充说明")
    private String detail;

    /**
     * 是否成功：1 成功 / 0 失败 / null 未判定
     */
    @TableField("success")
    @Schema(description = "1 成功 0 失败", example = "1")
    private Integer success;

    /**
     * 耗时（毫秒）
     */
    @TableField("cost_ms")
    @Schema(description = "耗时（毫秒）", example = "23")
    private Long costMs;

    /**
     * 操作人
     */
    @TableField("operator")
    @Schema(description = "操作人", example = "admin")
    private String operator;

    /**
     * 客户端 IP
     */
    @TableField("client_ip")
    @Schema(description = "客户端 IP", example = "192.168.50.101")
    private String clientIp;

    /**
     * 请求路径
     */
    @TableField("request_url")
    @Schema(description = "请求路径", example = "/api/apm/v1/config")
    private String requestUrl;

    /**
     * 操作时间
     */
    @TableField("create_time")
    @Schema(description = "操作时间", example = "2026-11-09T09:00:00")
    private LocalDateTime createTime;
}
