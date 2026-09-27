package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.model.NetProcessRowVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code netstat -ano} 输出解析，OpenSSH 与 WinRM 两条 Windows 通道共用。
 *
 * <p>不用 {@code Get-NetTCPConnection}：192.168.200.18（WS2012R2，管理员会话）实测 207 条连接的
 * OwningProcess 全为空，进程名列因此整列丢失；且该 cmdlet 只覆盖 TCP，UDP 端点在页面上完全缺席。
 * {@code netstat -ano} 一次给出 TCP/UDP、v4/v6 与 PID。</p>
 *
 * <p>表头随系统语言变化不影响解析：只认以 TCP/UDP 开头的行。UDP 行没有状态列，PID 即末列。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
final class WindowsNetstatRows {

    /**
     * 解析 netstat 输出。
     *
     * @param out   netstat 原始输出
     * @param names pid 到进程名的映射，键为 pid 字符串
     * @return 监听/连接行列表，顺序与远端输出一致
     */
    static List<NetProcessRowVO> parse(String out, Map<String, String> names) {
        List<NetProcessRowVO> list = new ArrayList<>();
        if (out == null || out.isBlank()) {
            return list;
        }
        for (String line : out.split("\\r?\\n")) {
            String[] col = line.trim().split("\\s+");
            boolean tcp;
            String state;
            String pid;
            if (col.length >= 5 && "TCP".equalsIgnoreCase(col[0])) {
                tcp = true;
                state = col[3];
                pid = col[4];
            } else if (col.length >= 4 && "UDP".equalsIgnoreCase(col[0])) {
                tcp = false;
                state = "";
                pid = col[3];
            } else {
                continue;
            }
            String[] local = split(col[1]);
            String[] peer = split(col[2]);
            String protocol = (tcp ? "tcp" : "udp") + (local[0].indexOf(':') >= 0 ? "6" : "4");
            list.add(NetProcessRowVO.of(protocol, local[0], toInt(local[1]), peer[0], toInt(peer[1]),
                    state, toInt(pid), names.getOrDefault(pid.trim(), "")));
        }
        return list;
    }

    /**
     * 拆开 netstat 的地址列。
     *
     * <p>IPv6 在不同版本上会写成 {@code [::]:22} 或 {@code :::22}，前者取括号内、后者按最后一个冒号切。</p>
     *
     * @param text 地址列原文
     * @return 长度为 2 的数组，[0] 地址、[1] 端口（缺省为 0）
     */
    private static String[] split(String text) {
        String s = text == null ? "" : text.trim();
        int close = s.indexOf(']');
        if (s.startsWith("[") && close > 0) {
            String addr = s.substring(1, close);
            String port = s.substring(close + 1);
            int colon = port.indexOf(':');
            return new String[]{addr, colon < 0 ? "0" : port.substring(colon + 1)};
        }
        int colon = s.lastIndexOf(':');
        if (colon < 0) {
            return new String[]{starToEmpty(s), "0"};
        }
        return new String[]{starToEmpty(s.substring(0, colon)), s.substring(colon + 1)};
    }

    /**
     * netstat 用 {@code *} 表示任意地址，本机与 Agent 通道用空串，统一成后者。
     *
     * @param addr 地址列拆出的地址
     * @return {@code *} 归一为空串，其余原样
     */
    private static String starToEmpty(String addr) {
        return "*".equals(addr) ? "" : addr;
    }

    /**
     * 宽松取整，非数字一律 0。
     *
     * @param text 原文
     * @return 数值
     */
    private static int toInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    /**
     * 工具类，禁止实例化。
     */
    private WindowsNetstatRows() {
    }
}
