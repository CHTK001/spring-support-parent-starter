package com.chua.starter.server.support.terminal.telnet;

import java.io.ByteArrayOutputStream;

/**
 * Telnet 协议协商过滤
 * <p>
 * Telnet 不是纯字节流：对端会在数据前插入 IAC（0xFF）命令，例如
 * {@code IAC DO ECHO}、{@code IAC WILL SGA}。这些字节直接丢给浏览器的 xterm，
 * 终端上就会滚出 {@code ÿýÿû} 之类的乱码，登录提示也会被吃掉。
 * </p>
 * <p>
 * 本类把桥接体两侧的字节按 Telnet 语义处理：
 * <pre>
 *   服务端 -&gt; 浏览器：剥掉 IAC 命令与子协商（SB ... SE），只留可显示数据；
 *                      对端的 WILL 回 DONT、DO 回 WONT（平台不做任何选项协商，
 *                      全部按 NVT 默认值工作，字符终端足够用）
 *   浏览器 -&gt; 服务端：把数据里的 0xFF 转义成 0xFF 0xFF（IAC 字面量规则）
 * </pre>
 * 状态机跨数据块保持，分包到达也能正确拼接。
 * </p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public final class TelnetNegotiation {

    /** IAC：解释为命令（Interpret As Command） */
    private static final int IAC = 255;

    /** SE：子协商结束 */
    private static final int SE = 240;

    /** SB：子协商开始 */
    private static final int SB = 250;

    /** WILL：对端愿意启用某选项 */
    private static final int WILL = 251;

    /** WONT：对端拒绝启用某选项 */
    private static final int WONT = 252;

    /** DO：请求本端启用某选项 */
    private static final int DO = 253;

    /** DONT：要求本端不要启用某选项 */
    private static final int DONT = 254;

    /** 数据态 */
    private static final int ST_DATA = 0;

    /** 已收到 IAC，等待命令字节 */
    private static final int ST_IAC = 1;

    /** 已收到 WILL/WONT/DO/DONT，等待选项字节 */
    private static final int ST_OPTION = 2;

    /** 子协商中 */
    private static final int ST_SB = 3;

    /** 子协商中收到 IAC，等待 SE */
    private static final int ST_SB_IAC = 4;

    /** 当前状态 */
    private int state = ST_DATA;

    /** 待回应的选项动词（WILL / DO） */
    private int optionVerb;

    /** 待回写给对端的字节（IAC 命令） */
    private final ByteArrayOutputStream replies = new ByteArrayOutputStream();

    /**
     * 处理远端输出：返回可显示数据，需回写的命令由 {@link #drainReplies()} 取走
     *
     * @param chunk 远端原始字节
     * @return 剥掉 IAC 命令后的显示数据（可能为空数组）
     */
    public byte[] filterServerData(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream visible = new ByteArrayOutputStream(chunk.length);
        for (byte raw : chunk) {
            int b = raw & 0xFF;
            switch (state) {
                case ST_DATA:
                    if (b == IAC) {
                        state = ST_IAC;
                    } else {
                        visible.write(b);
                    }
                    break;
                case ST_IAC:
                    if (b == IAC) {
                        // IAC IAC 是一个字面的 0xFF
                        visible.write(IAC);
                        state = ST_DATA;
                    } else if (b == WILL || b == WONT || b == DO || b == DONT) {
                        optionVerb = b;
                        state = ST_OPTION;
                    } else if (b == SB) {
                        state = ST_SB;
                    } else {
                        // NOP / GA / AYT 等单字节命令：无需处理
                        state = ST_DATA;
                    }
                    break;
                case ST_OPTION:
                    reply(optionVerb, b);
                    state = ST_DATA;
                    break;
                case ST_SB:
                    if (b == IAC) {
                        state = ST_SB_IAC;
                    }
                    break;
                case ST_SB_IAC:
                    state = b == SE ? ST_DATA : ST_SB;
                    break;
                default:
                    state = ST_DATA;
                    break;
            }
        }
        return visible.toByteArray();
    }

    /**
     * 取走待回写给对端的协商字节
     *
     * @return IAC 命令字节，无待回写时为空数组
     */
    public byte[] drainReplies() {
        byte[] pending = replies.toByteArray();
        replies.reset();
        return pending;
    }

    /**
     * 把浏览器输入里的 0xFF 转义，避免被对端当成命令起始
     *
     * @param chunk 浏览器输入字节
     * @return 可安全写入远端套接字的字节
     */
    public static byte[] escapeClientData(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(chunk.length);
        for (byte raw : chunk) {
            int b = raw & 0xFF;
            if (b == IAC) {
                out.write(IAC);
            }
            out.write(b);
        }
        return out.toByteArray();
    }

    /**
     * 把浏览器输入的换行统一成 NVT 的 CR LF
     * <p>
     * xterm 里回车只发一个 CR，Telnet 对端（尤其是 Windows Telnet 服务）按 NVT 规则
     * 期待 CR LF；直接发 CR 常表现为「敲了回车但命令没提交」。粘贴进来的 LF 同理。
     * </p>
     *
     * @param chunk 浏览器输入字节
     * @return 换行已展开为 CR LF 的字节
     */
    public static byte[] normalizeNewline(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(chunk.length + 8);
        for (int i = 0; i < chunk.length; i++) {
            int b = chunk[i] & 0xFF;
            if (b == '\r') {
                out.write('\r');
                out.write('\n');
                if (i + 1 < chunk.length && chunk[i + 1] == '\n') {
                    i++;
                }
            } else if (b == '\n') {
                out.write('\r');
                out.write('\n');
            } else {
                out.write(b);
            }
        }
        return out.toByteArray();
    }

    /**
     * 记录对端选项请求的回应：平台不启用任何选项
     *
     * @param verb 对端动词（WILL / DO）
     * @param opt  选项编码
     */
    private void reply(int verb, int opt) {
        if (verb == WILL) {
            // 对端愿意启用：我们不接受
            replies.write(IAC);
            replies.write(DONT);
            replies.write(opt);
        } else if (verb == DO) {
            // 对端要求我们启用：我们不启用
            replies.write(IAC);
            replies.write(WONT);
            replies.write(opt);
        }
        // WONT / DONT 是陈述，按协议无需回应
    }
}
