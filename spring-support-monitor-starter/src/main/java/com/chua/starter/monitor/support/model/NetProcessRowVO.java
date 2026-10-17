package com.chua.starter.monitor.support.model;

import java.util.Locale;

/**
 * 网络端口占用视图。
 *
 * <p>本机 OSHI、Agent HTTP、SSH {@code ss}/{@code lsof}、WinRM 四类通道共用同一套字段，
 * 前端不区分来源即可渲染。</p>
 *
 * @param protocol   协议（小写），如 tcp / tcp4 / udp
 * @param type       协议别名，与 protocol 同值，兼容旧前端取值
 * @param local      本地地址展示文本（addr:port）
 * @param localAddr  本地 IP
 * @param localPort  本地端口
 * @param remote     远端地址展示文本（addr:port）
 * @param remoteAddr 远端 IP
 * @param remotePort 远端端口
 * @param port       端口别名，与 localPort 同值
 * @param state      连接状态（大写），如 LISTEN / ESTABLISHED / UNCONN
 * @param pid        占用进程号，未知为 0
 * @param process    占用进程名，未知为空串
 * @author CH
 * @since 4.0.0.42
 */
public record NetProcessRowVO(
        String protocol,
        String type,
        String local,
        String localAddr,
        int localPort,
        String remote,
        String remoteAddr,
        int remotePort,
        int port,
        String state,
        int pid,
        String process) {

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
        return new NetProcessRowVO(
                proto,
                proto,
                localAddr + ":" + localPort,
                localAddr,
                localPort,
                remoteAddr + ":" + remotePort,
                remoteAddr,
                remotePort,
                localPort,
                normalizeState(proto, state),
                pid,
                pid <= 0 ? "" : (process == null ? "" : process));
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
