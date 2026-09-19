package com.chua.starter.monitor.support.model;

import lombok.Data;

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
        NetProcessRowVO row = new NetProcessRowVO();
        row.setProtocol(protocol);
        row.setType(protocol);
        row.setLocal(localAddr + ":" + localPort);
        row.setLocalAddr(localAddr);
        row.setLocalPort(localPort);
        row.setRemote(remoteAddr + ":" + remotePort);
        row.setRemoteAddr(remoteAddr);
        row.setRemotePort(remotePort);
        row.setPort(localPort);
        row.setState(state);
        row.setPid(pid);
        row.setProcess(process == null ? "" : process);
        return row;
    }
}
