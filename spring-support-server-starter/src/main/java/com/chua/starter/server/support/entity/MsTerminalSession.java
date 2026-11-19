package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 远程终端会话审计实体
 * <p>
 * 对应表 {@code server_terminal_session}，记录每次远控连接的协议、来源与生命周期。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "终端会话")
@TableName("server_terminal_session")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsTerminalSession extends SysBase {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
     * 主机 标识
     */
    @TableField("server_terminal_session_host_id")
    @Schema(description = "主机ID", example = "1")
    private Long serverTerminalSessionHostId; // 终端会话主机标识

    /**
     * 主机名称（冗余，便于列表展示）
     */
    @TableField("server_terminal_session_host_name")
    @Schema(description = "主机名称", example = "应用服务器-01")
    private String serverTerminalSessionHostName; // 终端会话主机名称

    /**
     * 协议类型
     */
    @TableField("server_terminal_session_protocol")
    @Schema(description = "协议: SSH / RDP / VNC / SFTP", example = "SSH")
    private String serverTerminalSessionProtocol; // 终端会话协议

    /**
     * 发起用户（前端框架透传）
     */
    @TableField("server_terminal_session_user")
    @Schema(description = "发起用户", example = "admin")
    private String serverTerminalSessionUser; // 终端会话用户

    /**
     * 客户端 IP
     */
    @TableField("server_terminal_session_client_ip")
    @Schema(description = "客户端IP", example = "192.168.1.100")
    private String serverTerminalSessionClientIp; // 终端会话客户端ip

    /**
     * 连接时间
     */
    @TableField("server_terminal_session_connect_time")
    @Schema(description = "连接时间")
    private LocalDateTime serverTerminalSessionConnectTime; // 终端会话连接时间

    /**
     * 关闭时间
     */
    @TableField("server_terminal_session_close_time")
    @Schema(description = "关闭时间")
    private LocalDateTime serverTerminalSessionCloseTime; // 终端会话关闭时间

    /**
     * 会话状态
     */
    @TableField("server_terminal_session_status")
    @Schema(description = "状态: OPEN / CLOSED", example = "OPEN")
    private String serverTerminalSessionStatus; // 终端会话状态

    /**
     * 关闭原因
     */
    @TableField("server_terminal_session_close_reason")
    @Schema(description = "关闭原因", example = "用户主动断开")
    private String serverTerminalSessionCloseReason; // 终端会话关闭原因
}
