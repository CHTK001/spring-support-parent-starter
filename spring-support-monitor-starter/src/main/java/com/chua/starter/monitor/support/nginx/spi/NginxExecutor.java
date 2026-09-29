package com.chua.starter.monitor.support.nginx.spi;

import com.chua.common.support.spi.annotations.Spi;

/**
 * Nginx 控制台的<strong>执行通道</strong>扩展点。
 *
 * <p>与 {@link NginxCommandProvider} 的分工：本接口只负责「把已经拼好的命令行送进目标
 * 并收回输出」，不负责命令文本，也不知道 systemd 或 taskkill 是什么。命令文本一律由
 * {@link NginxCommandProvider} 给出。</p>
 *
 * <p>这样切分的直接收益：同一条通道可以服务多个平台——SSH 通道既能连 Linux 也能连
 * Windows，差别只在命令文本与引号规则，而那是命令提供者的职责。反过来，同一个平台也可以
 * 有多个通道（SSH 连远端、本机直接执行），这正是 Windows 本地验收所需要的。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(NginxExecutor.SPI_NAME)
public interface NginxExecutor {

    /**
     * 扩展点名称。
     */
    String SPI_NAME = "nginx-executor";

    /**
     * 目标描述：一次执行需要的全部连接信息。
     *
     * @param host     主机地址；本机执行时为 {@code local}
     * @param port     端口
     * @param username 用户名
     * @param password 口令
     * @param local    是否在本机（控制台所在机器）执行
     */
    record Target(String host, int port, String username, String password, boolean local) {

        /**
         * 本机目标的判定约定。
         *
         * <p>用主机地址当「本机」的标记，是因为数据源表当下没有执行位置字段。取值定得
         * 刻意窄：只有空串与 {@code local}（不分大小写）算本机，{@code 127.0.0.1} /
         * {@code localhost} <strong>不算</strong>——那两个在数据源里就是「用 SSH 连本机」的
         * 既有含义，混在一起会让既有数据源悄悄改变执行方式。</p>
         *
         * @param host 数据源主机地址
         * @return 是本机标记返回 true
         */
        public static boolean isLocalMarker(String host) {
            String h = host == null ? "" : host.trim();
            return h.isEmpty() || "local".equalsIgnoreCase(h);
        }
    }

    /**
     * 一次命令执行的结果。
     *
     * @param exitCode 退出码；无法取得时为 -1
     * @param stdout   标准输出
     * @param stderr   标准错误
     */
    record ExecResult(int exitCode, String stdout, String stderr) {

        /**
         * 合并后的输出。多数 nginx 相关命令把信息写到 stderr（{@code nginx -V}、{@code systemctl show}），
         * 只读 stdout 会丢内容。
         *
         * @return stdout 与 stderr 的合并文本
         */
        public String output() {
            String out = stdout == null ? "" : stdout;
            String err = stderr == null ? "" : stderr;
            if (err.isEmpty()) {
                return out;
            }
            if (out.isEmpty()) {
                return err;
            }
            return out + "\n" + err;
        }
    }

    /**
     * 是否支持该目标。
     *
     * @param target 目标
     * @return 支持返回 true
     */
    boolean supports(Target target);

    /**
     * 扩展别名，用于日志与排障。
     *
     * @return 别名，如 {@code nginx-ssh}
     */
    String name();

    /**
     * 执行一条命令。
     *
     * @param target  目标
     * @param command 完整命令行（已由命令提供者按平台拼好）
     * @param seconds 超时秒数
     * @return 执行结果
     * @throws IllegalStateException 连接或执行失败
     */
    ExecResult exec(Target target, String command, long seconds);
}
