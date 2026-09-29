package com.chua.starter.oauth.client.support.impersonation;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.user.UserResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 临时令牌（管理员代入他人身份）的标记与校验。
 *
 * <p><b>为什么单独做一份过期校验</b>：静态协议的鉴权链路只判断「有没有携带令牌」
 * （{@code AbstractProtocol#hasValid}），并不比对 {@code expireTime}，
 * 也就是说常规静态令牌一旦签发就长期有效。这是框架既有行为，改它会把所有在线会话一起踢下线，
 * 不该在这里动。但临时令牌一旦失效就是安全漏洞：管理员关掉页面，令牌必须立刻作废。
 * 因此本类在鉴权时对<b>被标记为临时令牌</b>的那些额外强制过期与路径校验，
 * 对普通令牌完全不影响。</p>
 *
 * <p>标记信息随令牌走（存在令牌的 {@code ext} 里），不依赖服务端会话表，
 * 所以多实例部署不需要粘性会话。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class TemporaryToken {

    /**
     * 临时令牌标记键
     */
    public static final String EXT_TEMPORARY = "temporaryToken";

    /**
     * 允许访问的路径清单键（英文逗号分隔，{@code *} 表示不限）
     */
    public static final String EXT_ALLOWED_URLS = "temporaryAllowedUrls";

    /**
     * 授权人键：记录是哪个管理员开的这个临时令牌
     */
    public static final String EXT_GRANTED_BY = "temporaryGrantedBy";

    /**
     * 路径分隔符
     */
    private static final String PATH_SEPARATOR = ",";

    /**
     * 通配路径：不限制可访问范围
     */
    private static final String PATH_ANY = "*";

    private TemporaryToken() {
    }

    /**
     * 把一份身份标记为临时令牌。
     *
     * <p>同时把 {@code readonly} 置为 {@code true}：代入身份只应“看”，
     * 不应该让管理员以学生身份改学生自己的学习数据。</p>
     *
     * @param userResult    目标身份，会被就地写入标记信息
     * @param grantedBy     授权人标识（管理员），为空时记为未知
     * @param expireSeconds 有效期（秒）
     * @param allowedUrls   允许访问的路径前缀；为空或含 {@code *} 表示不限
     */
    public static void mark(UserResult userResult, String grantedBy, int expireSeconds, List<String> allowedUrls) {
        if (userResult == null) {
            return;
        }
        Map<String, Object> ext = userResult.getExt() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(userResult.getExt());
        ext.put(EXT_TEMPORARY, Boolean.TRUE);
        ext.put(EXT_GRANTED_BY, StringUtils.defaultString(grantedBy, "unknown"));
        if (allowedUrls != null && !allowedUrls.isEmpty()) {
            ext.put(EXT_ALLOWED_URLS, String.join(PATH_SEPARATOR, allowedUrls));
        }
        userResult.setExt(ext);
        userResult.setReadonly(true);
    }

    /**
     * 是否是临时令牌。
     *
     * @param userResult 身份信息
     * @return 带临时令牌标记返回 {@code true}
     */
    public static boolean isTemporary(UserResult userResult) {
        if (userResult == null || userResult.getExt() == null) {
            return false;
        }
        return Boolean.TRUE.equals(userResult.getExt().get(EXT_TEMPORARY));
    }

    /**
     * 临时令牌是否仍在有效期内。
     *
     * <p>普通令牌恒为可用：它们的过期语义由框架既有链路决定，本方法不介入。</p>
     *
     * @param userResult 身份信息
     * @return 可用返回 {@code true}；临时令牌已过期返回 {@code false}
     */
    public static boolean isUsable(UserResult userResult) {
        if (!isTemporary(userResult)) {
            return true;
        }
        Long expireTime = userResult.getExpireTime();
        if (expireTime == null) {
            // 临时令牌必须带有效期；缺失说明令牌被截断或手工构造，一律判不可用
            return false;
        }
        return expireTime > System.currentTimeMillis() / 1000L;
    }

    /**
     * 临时令牌是否允许访问给定路径。
     *
     * @param userResult 身份信息
     * @param path       请求路径
     * @return 允许返回 {@code true}；普通令牌或未限制路径时恒为 {@code true}
     */
    public static boolean isPathAllowed(UserResult userResult, String path) {
        if (!isTemporary(userResult)) {
            return true;
        }
        var allowed = splitAllowedUrls(userResult);
        if (allowed.isEmpty()) {
            return true;
        }
        if (StringUtils.isBlank(path)) {
            return false;
        }
        for (String prefix : allowed) {
            if (PATH_ANY.equals(prefix) || path.equals(prefix) || path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取出允许访问的路径清单。
     *
     * @param userResult 身份信息
     * @return 路径前缀列表；无限制时返回空列表
     */
    public static List<String> splitAllowedUrls(UserResult userResult) {
        var result = new ArrayList<String>();
        if (userResult == null || userResult.getExt() == null) {
            return result;
        }
        Object raw = userResult.getExt().get(EXT_ALLOWED_URLS);
        if (raw == null) {
            return result;
        }
        for (String part : String.valueOf(raw).split(PATH_SEPARATOR)) {
            var trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
