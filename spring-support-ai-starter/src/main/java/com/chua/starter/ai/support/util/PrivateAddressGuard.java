// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「本机 / 内网 / 链路本地」目标识别。
 *
 * <p>用途：普通账号写自己的设置覆盖层时，拦下把请求指向内网的取值。</p>
 *
 * <p><b>为什么字段名白名单不够。</b>写入侧原先只按「字段名含 {@code model}」放行，
 * 这是一份人工审计出来的结论（「35 个含 model 的字段全是模型选择项」）。
 * 审计会随字段增删悄悄失效：将来任何一个含 {@code model} 的字段只要语义上是个地址
 * （{@code model_base_url} 这种），白名单会直接放行；而模型 id 本身也可以被写成
 * {@code http://127.0.0.1:11434/v1} 这种带地址的形式。所以除了「哪些字段可写」，
 * 还要有「写了什么值不可接受」——本类就是后者。</p>
 *
 * <p><b>为什么不做 DNS 解析。</b>这里守的是「设置值」，不是即时请求：解析需要网络、
 * 会把一次写配置变成一次可能阻塞的调用，而且解析结果会随后续 DNS 变化而失效。
 * 因此本类只做**字面形态**识别（含整数/十六进制这两种最常见的混淆写法）。
 * 真正把请求发出去之前的出口校验仍应以后端既有能力为准，本类是纵深防御的一层。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
public final class PrivateAddressGuard {

    /**
     * 明文指向本机的名字。
     */
    private static final String[] LOCAL_NAMES = {
            "localhost", "0.0.0.0", "::1", "[::1]",
            "host.docker.internal", "host.internal", "gateway.docker.internal",
    };

    /**
     * 内网 / 回环 / 链路本地 / 云元数据段的 IPv4 字面量。
     *
     * <p>{@code 169.254.0.0/16} 单列出来是因为云上把实例元数据放在
     * {@code 169.254.169.254}，它是「读内网凭据」最常见的一个入口。</p>
     */
    private static final Pattern IPV4_PRIVATE = Pattern.compile(
            "\\b(?:127|10)\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"
                    + "|\\b192\\.168\\.\\d{1,3}\\.\\d{1,3}\\b"
                    + "|\\b172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}\\b"
                    + "|\\b169\\.254\\.\\d{1,3}\\.\\d{1,3}\\b");

    /**
     * 本地伪协议：读本机文件，等价于把主机暴露出去。
     */
    private static final Pattern LOCAL_SCHEME = Pattern.compile("\\b(?:file|jar|vfs|classpath)\\s*:", Pattern.CASE_INSENSITIVE);

    /**
     * 纯数字 token（长度 ≥ 8）：{@code 2130706433} 这类十进制 IP 混淆。
     *
     * <p>限制长度是为了不误伤短数字——端口、序号、并发数都是短数字，
     * 它们当成 32 位整数也会解读出各种地址，纯属噪声。</p>
     */
    private static final Pattern DECIMAL_IP = Pattern.compile("(?<![\\w.])\\d{8,10}(?![\\w.])");

    /**
     * 十六进制 token：{@code 0x7f000001} 这类混淆。
     */
    private static final Pattern HEX_IP = Pattern.compile("(?<![\\w.])0x[0-9a-f]{1,8}(?![\\w.])", Pattern.CASE_INSENSITIVE);

    private PrivateAddressGuard() {
    }

    /**
     * 判断取值里是否出现本机 / 内网目标。
     *
     * @param raw 待检查的取值（可为空）
     * @return true 表示看起来指向本机或内网
     */
    public static boolean looksLikePrivateTarget(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String value = raw.toLowerCase(Locale.ROOT);
        for (String name : LOCAL_NAMES) {
            if (value.contains(name)) {
                return true;
            }
        }
        // *.local（mDNS 局域网域名）与 .internal：前面必须有点，避免命中 foo-local 这类普通词
        if (value.contains(".local") || value.contains(".internal")) {
            return true;
        }
        if (IPV4_PRIVATE.matcher(value).find()) {
            return true;
        }
        if (LOCAL_SCHEME.matcher(value).find()) {
            return true;
        }
        if (matchesObfuscatedIp(value, DECIMAL_IP)) {
            return true;
        }
        return matchesObfuscatedIp(value, HEX_IP);
    }

    /**
     * 检查混淆写法的整数是否落在内网段。
     *
     * @param value   已转小写的取值
     * @param pattern 十进制或十六进制 token 的正则
     * @return true 表示存在一个解析后属于内网的整数
     */
    private static boolean matchesObfuscatedIp(String value, Pattern pattern) {
        Matcher m = pattern.matcher(value);
        while (m.find()) {
            String token = m.group();
            long parsed;
            try {
                parsed = token.startsWith("0x")
                        ? Long.parseLong(token.substring(2), 16)
                        : Long.parseLong(token);
            } catch (NumberFormatException ignore) {
                continue;
            }
            if (isPrivateIpv4(parsed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 32 位整数按大端拆成点分四段，再判是否内网。
     *
     * @param value 无符号 32 位整数
     * @return true 表示回环 / 内网 / 链路本地
     */
    private static boolean isPrivateIpv4(long value) {
        if (value <= 0 || value > 0xFFFFFFFFL) {
            return false;
        }
        long a = (value >>> 24) & 0xFF;
        long b = (value >>> 16) & 0xFF;
        return a == 127
                || a == 10
                || (a == 192 && b == 168)
                || (a == 172 && b >= 16 && b <= 31)
                || (a == 169 && b == 254);
    }
}