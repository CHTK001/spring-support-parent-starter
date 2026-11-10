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
 * 服务管理节点操作日志（一行 = 一次操作）。
 *
 * <p>与 {@code @SysLog} 切面的关系：切面把 {@code SysLoggerInfo} 发成事件，
 * {@code ServerOperLogListener} 是它唯一的消费者，负责把落在
 * {@code /ms/monitor/server} 前缀下、且能定位到具体节点的事件写进这张表。
 * 监听器只做「事件 → 行」的转换，采集时机与耗时统计仍由切面负责。</p>
 *
 * <p>{@code nodeName} 是冗余列：节点被改名或删除之后，历史日志仍要能读出
 * 「当时动的是哪个」，否则主语随记录一起消失，日志就只剩一串无主的动作。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "服务管理节点操作日志")
@TableName("ms_server_oper_log")
public class MsServerOperLog implements Serializable {

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
     * 节点 ID
     */
    @TableField("node_id")
    @Schema(description = "节点 ID", example = "5")
    private Long nodeId;

    /**
     * 节点名称（冗余）
     */
    @TableField("node_name")
    @Schema(description = "节点名称", example = "fs-demo")
    private String nodeName;

    /**
     * 操作码
     */
    @TableField("action")
    @Schema(description = "操作码", example = "START")
    private String action;

    /**
     * 操作中文名
     */
    @TableField("action_name")
    @Schema(description = "操作中文名", example = "启动节点")
    private String actionName;

    /**
     * 操作对象（协议/端口等可用于回溯的摘要）
     */
    @TableField("target")
    @Schema(description = "操作对象", example = "http://127.0.0.1:19080")
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
    @Schema(description = "请求路径", example = "/ms/monitor/server/5/start")
    private String requestUrl;

    /**
     * 操作时间
     */
    @TableField("create_time")
    @Schema(description = "操作时间", example = "2026-11-09T09:00:00")
    private LocalDateTime createTime;
}
