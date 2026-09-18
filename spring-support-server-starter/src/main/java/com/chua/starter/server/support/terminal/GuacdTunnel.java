package com.chua.starter.server.support.terminal;

import lombok.Getter;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.io.GuacamoleReader;
import org.apache.guacamole.io.GuacamoleWriter;
import org.apache.guacamole.net.GuacamoleSocket;
import org.apache.guacamole.net.InetGuacamoleSocket;
import org.apache.guacamole.protocol.ConfiguredGuacamoleSocket;
import org.apache.guacamole.protocol.GuacamoleClientInformation;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.apache.guacamole.protocol.GuacamoleInstruction;

import java.util.Map;

/**
* guacd 隧道封装（对齐 Orion-Visor 的 guacdtunnel）
*
* <p>封装 {@link GuacamoleConfiguration} 与 guacd 的握手、指令读写、参数设置。
* 支持 SSH/RDP/VNC 全部协议参数，包括 RDP 磁盘映射、远程app、VNC 红蓝交换等。</p>
*
* @author CH
* @since 4.0.0.42
 */
public class GuacdTunnel {

    /** 会话唯一标识 */
    private final String uuid;

    /** guacd 服务器地址 */
    private final String serverAddress;

    /** guacd 服务器端口 */
    private final int serverPort;

    /** 协议 */
    private final String protocol;

    /** 服务器配置 */
    @Getter
    private final GuacamoleConfiguration serverConfig; // 服务端配置

    /** 客户端信息 */
    @Getter
    private final GuacamoleClientInformation clientConfig; // 客户端配置

    /** guacd Socket */
    @Getter
    private GuacamoleSocket socket; // Socket

    /** 关闭标记 */
    private volatile boolean closed;

    /**
    * 构造隧道
    *
    * @param protocol      协议 ssh/vnc/rdp
    * @param uuid          会话标识
    * @param serverAddress guacd 地址
    * @param serverPort    guacd 端口
    */
    public GuacdTunnel(String protocol, String uuid, String serverAddress, int serverPort) {
        this.protocol = protocol;
        this.uuid = uuid;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.serverConfig = new GuacamoleConfiguration();
        this.clientConfig = new GuacamoleClientInformation();
        this.serverConfig.setProtocol(protocol);
        this.clientConfig.setName(uuid);
    }

    /**
    * 连接 guacd 并完成握手
    *
    * @throws GuacamoleException 连接或握手失败
    */
    public void connect() throws GuacamoleException {
        this.socket = new ConfiguredGuacamoleSocket(new InetGuacamoleSocket(serverAddress, serverPort), serverConfig, clientConfig);
    }

    /**
    * 设置目标主机
    *
    * @param address 地址
    * @param port    端口
    */
    public void remote(String address, int port) {
        this.setParameter("hostname", address);
        this.setParameter("port", port);
    }

    /**
    * 设置认证信息
    *
    * @param username 用户名
    * @param password 密码
    */
    public void auth(String username, String password) {
        this.setParameter("username", username);
        this.setParameter("password", password);
    }

    /**
    * 设置分辨率
    *
    * @param width  宽
    * @param height 高
    */
    public void size(int width, int height) {
        clientConfig.setOptimalScreenWidth(width);
        clientConfig.setOptimalScreenHeight(height);
        this.setParameter("width", width);
        this.setParameter("height", height);
    }

    /**
    * 设置 dpi
    *
    * @param dpi dpi
    */
    public void dpi(int dpi) {
        clientConfig.setOptimalResolution(dpi);
        this.setParameter("dpi", dpi);
    }

    /**
    * 设置时区
    *
    * @param timezone 时区
    */
    public void timezone(String timezone) {
        clientConfig.setTimezone(timezone);
        this.setParameter("timezone", timezone);
    }

    /**
    * 设置单个协议参数
    *
    * @param key   参数名
    * @param value 参数值
    */
    public void setParameter(String key, Object value) {
        if (value != null) {
            serverConfig.setParameter(key, String.valueOf(value));
        } else {
            serverConfig.unsetParameter(key);
        }
    }

    /**
    * 批量设置协议参数
    *
    * @param params 参数表
    */
    public void setParameter(Map<String, Object> params) {
        params.forEach(this::setParameter);
    }

    /**
    * 设置音频 MIME 类型
    *
    * @param mimeTypes 类型列表
    */
    public void setAudioMimeTypes(java.util.List<String> mimeTypes) {
        if (mimeTypes == null || mimeTypes.isEmpty()) {
            return;
        }
        clientConfig.getAudioMimetypes().addAll(mimeTypes);
    }

    /**
    * 写入 guacd 指令
    *
    * @param data 指令文本
    * @throws GuacamoleException 写入失败
    */
    public void write(String data) throws GuacamoleException {
        this.write(data.toCharArray());
    }

    /**
    * 写入 guacd 指令
    *
    * @param data 指令字节
    * @throws GuacamoleException 写入失败
    */
    public void write(byte[] data) throws GuacamoleException {
        this.write(new String(data));
    }

    /**
    * 写入 guacd 指令
    *
    * @param data 指令字符
    * @throws GuacamoleException 写入失败
    */
    public void write(char[] data) throws GuacamoleException {
        GuacamoleWriter writer = socket.getWriter();
        writer.write(data);
    }

    /**
    * 写入结构化指令
    *
    * @param opcode 操作码
    * @param args   参数
    * @throws GuacamoleException 写入失败
    */
    public void writeInstruction(String opcode, String... args) throws GuacamoleException {
        socket.getWriter().writeInstruction(new GuacamoleInstruction(opcode, args));
    }

    /**
    * 读取 guacd 输出
    *
    * @return 读取到的字符数组，EOF 返回 空
    * @throws GuacamoleException 读取失败
    */
    public char[] read() throws GuacamoleException {
        GuacamoleReader reader = socket.getReader();
        return reader.read();
    }

    /**
    * 是否打开
    *
    * @return 是否打开
    */
    public boolean isOpen() {
        return socket != null && socket.isOpen();
    }

    /**
    * 关闭隧道
    */
    public void close() {
        if (closed) {
            return;
        }
        this.closed = true;
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
    * 获取连接 标识
    *
    * @return 连接 标识
    */
    public String getConnectionId() {
        return serverConfig.getConnectionID();
    }
}
