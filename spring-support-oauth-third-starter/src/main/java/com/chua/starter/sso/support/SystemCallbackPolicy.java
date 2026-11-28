package com.chua.starter.sso.support;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 第三方登录「系统回调地址」校验策略（common 侧等价实现）
 *
 * <p><b>修复开放重定向（Open Redirect）缺陷</b>：{@code /v2/user/third/login/code} 的
 * {@code callback} 参数原样进 {@code state + "_system_callback"} 缓存，回调控制器取出后直接
 * {@code RedirectView#setUrl(...)} 重定向。攻击者用自有合法 OAuth 应用走一遍流程，即可让
 * 回调把受害者连同已下发的登录态弹到任意外部站点（钓鱼 → 账号接管）。</p>
 *
 * <p>oauth-starter 侧已有 {@code SystemCallbackPolicy}（守护 {@code /oauth2/*} 链路），但
 * common 模块<b>不依赖</b> oauth-starter，故在此提供等价实现守护 {@code /v2/third/login/*}
 * 链路，两者判定口径必须保持一致。</p>
 *
 * <p>判定顺序（任一步不通过即拒绝，绝不回退到「放行」）：</p>
 * <ol>
 *   <li>空值 → 视为 {@code "/"}（站内根路径，安全默认值）；</li>
 *   <li>含 CR/LF/空白/控制字符 → 拒绝（防响应头注入与解析歧义）；</li>
 *   <li>以 {@code /} 开头：必须<b>恰好一个</b>前导斜杠且第二位不是 {@code /}、{@code \}，
 *       否则视为协议相对 URL，拒绝；</li>
 *   <li>非相对路径：必须是合法的绝对 URI，仅接受 {@code http/https}；</li>
 *   <li>绝对地址的 {@code scheme://host[:port]} 前缀必须命中白名单，白名单为空
 *       → 绝对地址一律拒绝（fail-closed）。</li>
 * </ol>
 *
 * <p>入口（{@code thirdLoginCode}）用 {@link #rejectReason} 提前拒绝并回错误消息；
 * 缓存写入点用 {@link #sanitizeStructure} 做纵深防御；回调读取点用
 * {@link #sanitize} 在重定向前做最终兜底。</p>
 *
 * @since 2026/10/07
 * @author CH
 */
public final class SystemCallbackPolicy {

    /**
     * 安全默认值：站内根路径
     */
    private static final String DEFAULT_CALLBACK = "/";

    /**
     * 策略工具类
     */
    private SystemCallbackPolicy() {
    }

    /**
     * 校验并归一化系统回调地址（含白名单）
     *
     * @param rawCallback 原始回调地址（可能来自请求参数或 state 缓存）
     * @param allowedHosts 允许的绝对地址白名单（{@code scheme://host[:port]} 前缀）
     * @return 通过校验的回调地址；不合法时返回 {@link #DEFAULT_CALLBACK}
     */
    public static String sanitize(String rawCallback, List<String> allowedHosts) {
        if (!StringUtils.hasText(rawCallback)) {
            return DEFAULT_CALLBACK;
        }
        String callback = rawCallback.trim();
        if (callback.isEmpty()) {
            return DEFAULT_CALLBACK;
        }
        if (containsControlChar(callback)) {
            return DEFAULT_CALLBACK;
        }
        if (callback.startsWith("/")) {
            return isSafeRelative(callback) ? callback : DEFAULT_CALLBACK;
        }
        return isAllowedAbsolute(callback, getAllowedHosts(allowedHosts)) ? callback : DEFAULT_CALLBACK;
    }

    /**
     * 判断回调地址是否合法（不合法时给出明确原因，便于日志告警与前端提示）
     *
     * @param rawCallback 原始回调地址
     * @param allowedHosts 允许的绝对地址白名单
     * @return {@code null} 表示合法，否则返回拒绝原因
     */
    public static String rejectReason(String rawCallback, List<String> allowedHosts) {
        if (!StringUtils.hasText(rawCallback)) {
            return null;
        }
        String callback = rawCallback.trim();
        if (containsControlChar(callback)) {
            return "回调地址包含控制字符或空白";
        }
        if (callback.startsWith("/")) {
            return isSafeRelative(callback) ? null : "回调地址为协议相对 URL，已拒绝（疑似开放重定向）";
        }
        List<String> allowed = getAllowedHosts(allowedHosts);
        if (allowed.isEmpty()) {
            return "未配置 allowed-system-callback-hosts，绝对地址回调已拒绝（疑似开放重定向）";
        }
        return isAllowedAbsolute(callback, allowed) ? null : "回调地址不在 allowed-system-callback-hosts 白名单内";
    }

    /**
     * 结构消毒（不含白名单，用于缓存写入点的纵深防御）
     *
     * <p>拒绝控制字符、协议相对 URL（{@code //evil.tld}、{@code /\evil.tld}）与
     * 非 http(s) 的绝对地址；合法绝对地址原样保留，其白名单判定推迟到读取点
     * （读取点持有 {@code plugin.system.security.allowed-system-callback-hosts} 配置）。</p>
     *
     * @param rawCallback 原始回调地址
     * @return 结构安全的回调地址，不合法时返回 {@link #DEFAULT_CALLBACK}
     */
    public static String sanitizeStructure(String rawCallback) {
        if (!StringUtils.hasText(rawCallback)) {
            return DEFAULT_CALLBACK;
        }
        String callback = rawCallback.trim();
        if (callback.isEmpty()) {
            return DEFAULT_CALLBACK;
        }
        if (containsControlChar(callback)) {
            return DEFAULT_CALLBACK;
        }
        if (callback.startsWith("/")) {
            return isSafeRelative(callback) ? callback : DEFAULT_CALLBACK;
        }
        URI uri = parseUri(callback);
        if (uri == null || !uri.isAbsolute() || uri.getRawAuthority() == null) {
            return DEFAULT_CALLBACK;
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return DEFAULT_CALLBACK;
        }
        return callback;
    }

    /**
     * 是否包含控制字符或空白（{@code <= 0x20} 及 DEL，防响应头注入与解析歧义）
     *
     * @param value 待检查字符串
     * @return true 表示包含
     */
    private static boolean containsControlChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }

    /**
     * 站内相对地址是否安全
     *
     * <p>必须以单个 {@code /} 开头：第二位再出现 {@code /} 即协议相对 URL
     * （{@code //evil.tld}），出现 {@code \} 为等价绕过（{@code /\evil.tld}）。</p>
     *
     * @param callback 以 {@code /} 开头的回调地址
     * @return true 表示安全
     */
    private static boolean isSafeRelative(String callback) {
        if (callback.length() == 1) {
            return true;
        }
        char second = callback.charAt(1);
        return second != '/' && second != '\\';
    }

    /**
     * 绝对地址是否命中白名单（归一化 {@code scheme://host[:port]} <b>精确相等</b>）
     *
     * <p>刻意用精确相等而非前缀包含：前缀包含会让 {@code app.example.com.evil.tld}
     * 命中 {@code app.example.com}（后缀绕过），也让 {@code :8443} 命中默认端口
     * （跨源绕过）——两者都是真实可用的开放重定向手法。</p>
     *
     * @param callback 绝对回调地址
     * @param allowedHosts 白名单
     * @return true 表示允许
     */
    private static boolean isAllowedAbsolute(String callback, List<String> allowedHosts) {
        String origin = originPrefix(callback);
        if (origin == null) {
            return false;
        }
        for (String allowed : allowedHosts) {
            if (origin.equals(normalizeAllowed(allowed))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 回调地址的归一化 origin：{@code scheme://host[:port]} 全小写
     *
     * <p>非法 URI、无 host、非 http(s) 协议一律返回 {@code null}（拒绝）。</p>
     *
     * @param value 回调地址
     * @return 归一化 origin，不可解析或不接受时为 {@code null}
     */
    private static String originPrefix(String value) {
        URI uri = parseUri(value);
        if (uri == null || !uri.isAbsolute() || !StringUtils.hasText(uri.getHost())) {
            return null;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return null;
        }
        return buildPrefix(scheme, uri.getHost(), uri.getPort());
    }

    /**
     * 构造 {@code scheme://host[:port]} 归一化前缀（全小写，未显式端口则省略）
     *
     * @param scheme 协议（小写）
     * @param host   主机
     * @param port   端口，-1 表示未显式指定
     * @return 前缀
     */
    private static String buildPrefix(String scheme, String host, int port) {
        StringBuilder sb = new StringBuilder(scheme.toLowerCase(Locale.ROOT)).append("://")
                .append(host.toLowerCase(Locale.ROOT));
        if (port > 0) {
            sb.append(':').append(port);
        }
        return sb.toString();
    }

    /**
     * 归一化白名单项：有 scheme+host 时取 {@code scheme://host[:port]}（忽略路径/查询/大小写），
     * 否则回退为小写并去掉尾部 {@code /}（非法项匹配不上即拒绝）
     *
     * @param entry 白名单项
     * @return 归一化前缀
     */
    private static String normalizeAllowed(String entry) {
        URI uri = parseUri(entry);
        if (uri != null && StringUtils.hasText(uri.getScheme()) && StringUtils.hasText(uri.getHost())) {
            return buildPrefix(uri.getScheme(), uri.getHost(), uri.getPort());
        }
        String value = entry.toLowerCase(Locale.ROOT);
        return value.replaceAll("/+$", "");
    }

    /**
     * 解析 URI
     *
     * @param value 待解析地址
     * @return URI，语法非法时返回 null
     */
    private static URI parseUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * 归一化白名单：去空白、丢弃空白项（非法项由 {@link #normalizeAllowed} 兜底）
     *
     * @param allowedHosts 原始白名单
     * @return 归一化后的白名单
     */
    private static List<String> getAllowedHosts(List<String> allowedHosts) {
        if (CollectionUtils.isEmpty(allowedHosts)) {
            return List.of();
        }
        return allowedHosts.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }
}
