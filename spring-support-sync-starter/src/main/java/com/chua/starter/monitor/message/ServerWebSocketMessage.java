package com.chua.starter.sync.message;

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
     * 创建RDP连接消息
     */
    public static ServerWebSocketMessage createRdpConnectMessage(Integer serverId, String serverName,
                                                                 String serverHost, Integer serverPort) {
        return ServerWebSocketMessage.builder().messageType(MessageType.RDP_CONNECT).serverId(serverId)
                .serverName(serverName).serverHost(serverHost).serverPort(serverPort)
                .serverProtocol("RDP").connectTime(LocalDateTime.now())
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建RDP断开连接消息
     */
    public static ServerWebSocketMessage createRdpDisconnectMessage(Integer serverId, String serverName,
                                                                    String reason) {
        return ServerWebSocketMessage.builder().messageType(MessageType.RDP_DISCONNECT).serverId(serverId)
                .serverName(serverName).errorMessage(reason)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建RDP错误消息
     */
    public static ServerWebSocketMessage createRdpErrorMessage(Integer serverId, String serverName,
                                                               String errorMessage) {
        return ServerWebSocketMessage.builder().messageType(MessageType.RDP_ERROR).serverId(serverId)
                .serverName(serverName).errorMessage(errorMessage)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建RDP数据消息
     */
    public static ServerWebSocketMessage createRdpDataMessage(Integer serverId, String serverName,
                                                              Object data) {
        return ServerWebSocketMessage.builder().messageType(MessageType.RDP_DATA).serverId(serverId)
                .serverName(serverName).data(data)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建RDP输入消息
     */
    public static ServerWebSocketMessage createRdpInputMessage(Integer serverId, String serverName,
                                                               Object data) {
        return ServerWebSocketMessage.builder().messageType(MessageType.RDP_INPUT).serverId(serverId)
                .serverName(serverName).data(data)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建VNC连接消息
     */
    public static ServerWebSocketMessage createVncConnectMessage(Integer serverId, String serverName,
                                                                 String serverHost, Integer serverPort) {
        return ServerWebSocketMessage.builder().messageType(MessageType.VNC_CONNECT).serverId(serverId)
                .serverName(serverName).serverHost(serverHost).serverPort(serverPort)
                .serverProtocol("VNC").connectTime(LocalDateTime.now())
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建VNC断开连接消息
     */
    public static ServerWebSocketMessage createVncDisconnectMessage(Integer serverId, String serverName,
                                                                    String reason) {
        return ServerWebSocketMessage.builder().messageType(MessageType.VNC_DISCONNECT).serverId(serverId)
                .serverName(serverName).errorMessage(reason)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建VNC错误消息
     */
    public static ServerWebSocketMessage createVncErrorMessage(Integer serverId, String serverName,
                                                               String errorMessage) {
        return ServerWebSocketMessage.builder().messageType(MessageType.VNC_ERROR).serverId(serverId)
                .serverName(serverName).errorMessage(errorMessage)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建VNC数据消息
     */
    public static ServerWebSocketMessage createVncDataMessage(Integer serverId, String serverName,
                                                              Object data) {
        return ServerWebSocketMessage.builder().messageType(MessageType.VNC_DATA).serverId(serverId)
                .serverName(serverName).data(data)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建VNC输入消息
     */
    public static ServerWebSocketMessage createVncInputMessage(Integer serverId, String serverName,
                                                               Object data) {
        return ServerWebSocketMessage.builder().messageType(MessageType.VNC_INPUT).serverId(serverId)
                .serverName(serverName).data(data)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建远程桌面连接消息
     */
    public static ServerWebSocketMessage createRemoteDesktopConnectMessage(Integer serverId, String serverName,
                                                                           String serverHost, Integer serverPort,
                                                                           String webSocketUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("websocketUrl", webSocketUrl);
        return ServerWebSocketMessage.builder().messageType(MessageType.REMOTE_DESKTOP_CONNECT).serverId(serverId)
                .serverName(serverName).serverHost(serverHost).serverPort(serverPort)
                .serverProtocol("REMOTE_DESKTOP").data(data).connectTime(LocalDateTime.now())
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建远程桌面断开连接消息
     */
    public static ServerWebSocketMessage createRemoteDesktopDisconnectMessage(Integer serverId, String serverName,
                                                                              String reason) {
        return ServerWebSocketMessage.builder().messageType(MessageType.REMOTE_DESKTOP_DISCONNECT).serverId(serverId)
                .serverName(serverName).errorMessage(reason)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建远程桌面错误消息
     */
    public static ServerWebSocketMessage createRemoteDesktopErrorMessage(Integer serverId, String serverName,
                                                                         String errorMessage) {
        return ServerWebSocketMessage.builder().messageType(MessageType.REMOTE_DESKTOP_ERROR).serverId(serverId)
                .serverName(serverName).errorMessage(errorMessage)
                .dataId(serverId != null ? String.valueOf(serverId) : null)
                .timestamp(System.currentTimeMillis()).build();
    }

    /**
     * 创建服务器指标消息
     */
    public static ServerWebSocketMessage createServerMetricsMessage(Integer serverId, String serverName,
                                                                    Object metrics) {
        return ServerWebSocketMessage.builder().messageType(MessageType.SERVER_METRICS).serverId(serverId)
                .serverName(serverName).data(metrics)
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
        String SSH_INPUT = "ssh_input";
        String SSH_RESIZE = "ssh_resize";
        String SHELL_OUTPUT = "shell_output";
        String SHELL_INPUT = "shell_input";
        String SHELL_ERROR = "shell_error";
        String RDP_CONNECT = "rdp_connect";
        String RDP_INPUT = "rdp_input";
        String RDP_DISCONNECT = "rdp_disconnect";
        String RDP_RESIZE = "rdp_resize";
        String RDP_DATA = "rdp_data";
        String RDP_ERROR = "rdp_error";
        String VNC_CONNECT = "vnc_connect";
        String VNC_INPUT = "vnc_input";
        String VNC_DISCONNECT = "vnc_disconnect";
        String VNC_RESIZE = "vnc_resize";
        String VNC_DATA = "vnc_data";
        String VNC_ERROR = "vnc_error";
        String REMOTE_DESKTOP_CONNECT = "remote_desktop_connect";
        String REMOTE_DESKTOP_DISCONNECT = "remote_desktop_disconnect";
        String REMOTE_DESKTOP_ERROR = "remote_desktop_error";
        String REMOTE_DESKTOP_MOUSE_MOVE = "remote_desktop_mouse_move";
        String REMOTE_DESKTOP_MOUSE_CLICK = "remote_desktop_mouse_click";
        String REMOTE_DESKTOP_MOUSE_WHEEL = "remote_desktop_mouse_wheel";
        String REMOTE_DESKTOP_KEY_PRESS = "remote_desktop_key_press";
        String REMOTE_DESKTOP_KEY_RELEASE = "remote_desktop_key_release";
        String SERVER_CREATED = "server_created";
        String SERVER_UPDATED = "server_updated";
        String SERVER_DELETE = "server_delete";
        String SERVER_HEALTH = "server_health";
        String SERVER_LATENCY = "server_latency";
        String BATCH_SERVER_LATENCY = "batch_server_latency";
        String SERVER_METRICS = "server_metrics";
    }
}
