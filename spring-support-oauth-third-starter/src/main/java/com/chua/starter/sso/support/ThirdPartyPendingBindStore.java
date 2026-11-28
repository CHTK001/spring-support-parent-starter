package com.chua.starter.sso.support;

import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthUser;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 三方登录「待绑定」身份的服务端一次性缓存
 *
 * <p>三方登录回调发现第三方账号尚未绑定本地用户时，回调控制器不再直接以失败告终，
 * 而是把三方身份（uid/用户名/昵称/头像/邮箱）暂存为本凭证，302 回前端并携带
 * {@code bindToken}；前端凭 token 回显三方身份，用户输入本地账号密码后调
 * {@code /v2/user/third/bind/complete} 核验并落绑定关系，绑定完成后才签发令牌。</p>
 *
 * <p>凭证特性对齐换票凭证 {@code redirectParam}：仅存服务端、一次性消费、短时效
 * （5 分钟），防止重放与长期挂起的三方身份。</p>
 *
 * @since 2026/11/21
 * @author CH
 */
@Slf4j
public final class ThirdPartyPendingBindStore {

    /**
     * 待绑定身份快照
     *
     * @param bindType     落库第三方类型（各平台存量口径：gitee/wechat 小写，其余为大写枚举名）
     * @param extLoginType 签发令牌 ext 使用的 loginType/thirdPartyType（小写平台名）
     * @param uid          三方唯一标识
     * @param username     三方用户名
     * @param nickname     三方昵称
     * @param avatar       三方头像
     * @param email        三方邮箱
     */
    public record PendingBind(
            String bindType,
            String extLoginType,
            String uid,
            String username,
            String nickname,
            String avatar,
            String email
    ) {
    }

    /**
     * 凭证有效期（毫秒）：5 分钟内完成绑定，超时需重新发起三方登录
     */
    private static final long EXPIRE_MILLIS = 5 * 60 * 1000L;

    /**
     * 凭证存储（token -&gt; 条目）
     */
    private static final Map<String, Entry> STORE = new ConcurrentHashMap<>();

    /**
     * 凭证随机熵源
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 存储条目：身份 + 过期时间
     *
     * @param bind     待绑定身份
     * @param expireAt 过期时间（毫秒时间戳）
     */
    private record Entry(PendingBind bind, long expireAt) {
    }

    /**
     * 工具类
     */
    private ThirdPartyPendingBindStore() {
    }

    /**
     * 暂存三方身份并签发一次性 bindToken
     *
     * @param bindType     落库第三方类型
     * @param extLoginType 签发令牌 ext 使用的登录类型
     * @param authUser     三方授权用户
     * @return 一次性待绑定凭证
     */
    public static String create(String bindType, String extLoginType, AuthUser authUser) {
        evictExpired();
        String token = "bind_" + UUID.randomUUID().toString().replace("-", "")
                + Long.toHexString(RANDOM.nextLong());
        PendingBind bind = new PendingBind(
                bindType,
                extLoginType,
                authUser.getUuid(),
                authUser.getUsername(),
                authUser.getNickname(),
                authUser.getAvatar(),
                authUser.getEmail());
        STORE.put(token, new Entry(bind, System.currentTimeMillis() + EXPIRE_MILLIS));
        log.info("三方待绑定凭证签发: platform={}, uid={}", extLoginType, authUser.getUuid());
        return token;
    }

    /**
     * 读取待绑定身份（不核销，供绑定页回显；complete 时才消费）
     *
     * @param token 待绑定凭证
     * @return 待绑定身份；凭证缺失或已过期返回 {@code null}
     */
    public static PendingBind peek(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        Entry entry = STORE.get(token);
        if (null == entry || entry.expireAt() <= System.currentTimeMillis()) {
            return null;
        }
        return entry.bind();
    }

    /**
     * 一次性核销待绑定身份：凭证存在且未过期则移除并返回，否则返回 {@code null}
     *
     * @param token 待绑定凭证
     * @return 待绑定身份；缺失、已使用或已过期返回 {@code null}
     */
    public static PendingBind consume(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        AtomicReference<Entry> consumed = new AtomicReference<>();
        STORE.computeIfPresent(token, (key, entry) -> {
            if (entry.expireAt() > System.currentTimeMillis()) {
                consumed.set(entry);
            }
            // 命中即移除（有效与过期都移除，保证一次一用）
            return null;
        });
        Entry entry = consumed.get();
        return null == entry ? null : entry.bind();
    }

    /**
     * 清理过期凭证，防止长期未完成绑定导致内存缓慢增长
     */
    private static void evictExpired() {
        long now = System.currentTimeMillis();
        STORE.entrySet().removeIf(entry -> entry.getValue().expireAt() <= now);
    }
}
