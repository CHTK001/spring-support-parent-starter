package com.chua.starter.monitor.support.model;

import lombok.Data;

import java.util.Locale;

/**
 * 网络端口占用视图。
 *
 * <p>本机 OSHI、Agent HTTP、SSH {@code ss}/{@code lsof}、WinRM 四类通道共用同一套字段，
 * 前端不区分来源即可渲染。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class NetProcessRowVO {

    /**
     * 协议（小写），如 tcp / tcp4 / udp
     */
    private String protocol;

    /**
     * 协议别名，与 protocol 同值，兼容旧前端取值
     */
    private String type;

    /**
     * 本地地址展示文本（addr:port）
     */
    private String local;

    /**
     * 本地 IP
     */
    private String localAddr;

    /**
     * 本地端口
     */
    private int localPort;

    /**
     * 远端地址展示文本（addr:port）
     */
    private String remote;

    /**
     * 远端 IP
     */
    private String remoteAddr;

    /**
     * 远端端口
     */
    private int remotePort;

    /**
     * 端口别名，与 localPort 同值
     */
    private int port;

    /**
     * 连接状态（大写），如 LISTEN / ESTABLISHED / UNCONN
     */
    private String state;

    /**
     * 占用进程号，未知为 0
     */
    private int pid;

    /**
     * 占用进程名，未知为空串
     */
    private String process;

    /**
     * 按本地/远端地址四元组构造一行监听记录。
     *
     * @param protocol   协议，如 tcp4 / udp
     * @param localAddr  本地 IP
     * @param localPort  本地端口
     * @param remoteAddr 远端 IP，未连接时传空串
     * @param remotePort 远端端口
     * @param state      连接状态
     * @param pid        占用进程号，未知传 0
     * @param process    占用进程名，未知传空串
     * @return 填充完毕的视图对象
     */
    public static NetProcessRowVO of(String protocol, String localAddr, int localPort, String remoteAddr,
                                    int remotePort, String state, int pid, String process) {
        String proto = protocol == null ? "" : protocol.trim().toLowerCase(Locale.ROOT);
        NetProcessRowVO row = new NetProcessRowVO();
        row.setProtocol(proto);
        row.setType(proto);
        row.setLocal(localAddr + ":" + localPort);
        row.setLocalAddr(localAddr);
        row.setLocalPort(localPort);
        row.setRemote(remoteAddr + ":" + remotePort);
        row.setRemoteAddr(remoteAddr);
        row.setRemotePort(remotePort);
        row.setPort(localPort);
        row.setState(normalizeState(proto, state));
        row.setPid(pid);
        row.setProcess(pid <= 0 ? "" : (process == null ? "" : process));
        return row;
    }

    /**
     * 把各通道的状态写法折叠到同一词表。
     *
     * <p>ss 用 {@code ESTAB}/{@code TIME-WAIT}，WinRM 用 {@code TimeWait}，netstat 用 {@code LISTENING}，
     * OSHI 用 {@code TIME_WAIT}，同一含义多种写法会让前端的筛选与配色按通道分裂，故在行工厂统一为 OSHI 的大写下划线口径。</p>
     *
     * @param protocol 已归一为小写的协议
     * @param state    通道原始状态，可为 null
     * @return 统一后的状态；无状态时 UDP 给 UNCONN、其余给 UNKNOWN
     */
    private static String normalizeState(String protocol, String state) {
        String raw = state == null ? "" : state.trim();
        if (raw.isEmpty() || "NONE".equalsIgnoreCase(raw)) {
            return protocol.startsWith("udp") ? "UNCONN" : "UNKNOWN";
        }
        String normalized = raw.replaceAll("(?<=[a-z0-9])(?=[A-Z])", "_")
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT)
                .replaceAll("_(?=\\d)", "");
        if ("LISTENING".equals(normalized)) {
            return "LISTEN";
        }
        return "ESTAB".equals(normalized) ? "ESTABLISHED" : normalized;
    }
}
