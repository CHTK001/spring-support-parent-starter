package com.chua.starter.monitor.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 服务器WebSocket消息实体
 *
 * @author CH
 * @since 2024/12/23
 * @version 1.0.0
 */
@Schema(description = "服务器WebSocket消息")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerWebSocketMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型
     */
    @Schema(description = "消息类型")
    private String messageType;

    /**
     * 服务器ID
     */
    @Schema(description = "服务器ID")
    private Integer serverId;

    /**
     * 服务器名称
     */
    @Schema(description = "服务器名称")
    private String serverName;

    /**
     * 服务器主机地址
     */
    @Schema(description = "服务器主机地址")
    private String serverHost;

    /**
     * 服务器端口
     */
    @Schema(description = "服务器端口")
    private Integer serverPort;

    /**
     * 连接协议
     */
    @Schema(description = "连接协议")
    private String serverProtocol;

    /**
     * 连接状态
     */
    @Schema(description = "连接状态")
    private Integer connectionStatus;

    /**
     * 状态描述
     */
    @Schema(description = "状态描述")
    private String statusDesc;

    /**
     * 错误消息
     */
    @Schema(description = "错误消息")
    private String errorMessage;

    /**
     * 响应时间(毫秒)
     */
    @Schema(description = "响应时间(毫秒)")
    private Long responseTime;

    /**
     * 连接时间
     */
    @Schema(description = "连接时间")
    private LocalDateTime connectTime;

    /**
     * 消息时间戳
     */
    @Schema(description = "消息时间戳")
    private Long timestamp;

    /**
     * 数据ID
     * 用于前端过滤消息，统一标识关联数据的ID
     * 可以是 serverId、containerId、softId 等
     */
    @Schema(description = "数据ID，用于前端过滤消息")
    private String dataId;

    /**
     * 扩展数据
     */
    @Schema(description = "扩展数据")
    private Object data;

    /**
     * 创建连接状态变化消息
     */
    public static ServerWebSocketMessage createConnectionStatusMessage(Integer serverId, String serverName,
                                                                       String serverHost, Integer serverPort, String serverProtocol, Integer connectionStatus,
                                                                       String statusDesc, String errorMessage, Long responseTime) {
        return ServerWebSocketMessage.builder().messageType(MessageType.CONNECTION_STATUS_CHANGE)
                .serverId(serverId).serverName(serverName).serverHost(serverHost).serverPort(serverPort)
                .serverProtocol(serverProtocol).connectionStatus(connectionStatus)
                .statusDesc(statusDesc).errorMessage(errorMessage).responseTime(responseTime)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .connectTime(LocalDateTime.now()).timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建SSH数据消息
     */
    public static ServerWebSocketMessage createSSHDataMessage(Integer serverId, String serverName, String data) {
        return ServerWebSocketMessage.builder().messageType(MessageType.SSH_DATA).serverId(serverId)
                .serverName(serverName).data(data)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建SSH连接消息
     */
    public static ServerWebSocketMessage createSSHConnectMessage(Integer serverId, String serverName,
                                                                 String serverHost, Integer serverPort) {
        return ServerWebSocketMessage.builder().messageType(MessageType.SSH_CONNECT).serverId(serverId)
                .serverName(serverName).serverHost(serverHost).serverPort(serverPort)
                .serverProtocol("SSH").connectTime(LocalDateTime.now())
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建SSH断开连接消息
     */
    public static ServerWebSocketMessage createSSHDisconnectMessage(Integer serverId, String serverName,
                                                                    String reason) {
        return ServerWebSocketMessage.builder().messageType(MessageType.SSH_DISCONNECT).serverId(serverId)
                .serverName(serverName).errorMessage(reason)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建SSH错误消息
     */
    public static ServerWebSocketMessage createSSHErrorMessage(Integer serverId, String serverName,
                                                               String errorMessage) {
        return ServerWebSocketMessage.builder().messageType(MessageType.SSH_ERROR).serverId(serverId)
                .serverName(serverName).errorMessage(errorMessage)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建Shell输出消息
     */
    public static ServerWebSocketMessage createShellOutputMessage(Integer serverId, String serverName,
                                                                  String output) {
        Map<String, Object> shellData = new HashMap<>();
        shellData.put("output", output);
        shellData.put("type", "output");

        return ServerWebSocketMessage.builder().messageType(MessageType.SHELL_OUTPUT).serverId(serverId)
                .serverName(serverName).data(shellData)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建Shell输入消息
     */
    public static ServerWebSocketMessage createShellInputMessage(Integer serverId, String serverName,
                                                                 String input) {
        Map<String, Object> shellData = new HashMap<>();
        shellData.put("input", input);
        shellData.put("type", "input");

        return ServerWebSocketMessage.builder().messageType(MessageType.SHELL_INPUT).serverId(serverId)
                .serverName(serverName).data(shellData)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建Shell错误消息
     */
    public static ServerWebSocketMessage createShellErrorMessage(Integer serverId, String serverName,
                                                                 String error) {
        Map<String, Object> shellData = new HashMap<>();
        shellData.put("error", error);
        shellData.put("type", "error");

        return ServerWebSocketMessage.builder().messageType(MessageType.SHELL_ERROR).serverId(serverId)
                .serverName(serverName).data(shellData)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 消息类型常量
     */
    public interface MessageType {
        String CONNECTION_STATUS_CHANGE = "connection_status_change";
        String SSH_DATA = "ssh_data";
        String SSH_CONNECT = "ssh_connect";
        String SSH_DISCONNECT = "ssh_disconnect";
        String SSH_ERROR = "ssh_error";
        String SHELL_OUTPUT = "shell_output";
        String SHELL_INPUT = "shell_input";
        String SHELL_ERROR = "shell_error";
    }
}
