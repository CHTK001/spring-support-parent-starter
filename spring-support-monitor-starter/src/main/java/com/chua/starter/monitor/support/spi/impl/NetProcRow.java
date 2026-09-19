package com.chua.starter.monitor.support.spi.impl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网络监听行的统一构造器。
 *
 * <p>本机 OSHI、Agent HTTP、SSH {@code ss}、WinRM 四类通道都要输出同一套字段，前端才能不区分来源直接渲染。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
final class NetProcRow {

    /**
     * 工具类，禁止实例化。
     */
    private NetProcRow() {
    }

    /**
     * 构造一行网络监听记录。
     *
     * @param protocol   协议，如 tcp4 / udp / tcp
     * @param localAddr  本地 IP
     * @param localPort  本地端口
     * @param remoteAddr 远端 IP，未连接时可为空串
     * @param remotePort 远端端口
     * @param state      连接状态，如 LISTEN / ESTABLISHED
     * @param pid        占用进程标识，未知传 0
     * @param process    占用进程名，未知传空串
     * @return 前端可直接消费的行 Map
     */
    static Map<String, Object> of(String protocol, String localAddr, int localPort, String remoteAddr,
                                  int remotePort, String state, int pid, String process) {
        String local = localAddr + ":" + localPort;
        String remote = remoteAddr + ":" + remotePort;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("protocol", protocol);
        m.put("type", protocol);
        m.put("local", local);
        m.put("localAddr", localAddr);
        m.put("localPort", localPort);
        m.put("remote", remote);
        m.put("remoteAddr", remoteAddr);
        m.put("remotePort", remotePort);
        m.put("port", localPort);
        m.put("state", state);
        m.put("pid", pid);
        m.put("process", process == null ? "" : process);
        return m;
    }
}
