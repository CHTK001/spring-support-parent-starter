package com.chua.starter.oauth.client.support.user;

import com.chua.common.support.core.utils.DigestUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * OpenID 和 UnionID 生成器
 * <p>
 * 用于生成第三方登录场景下的用户唯一标识
 * </p>
 *
 * <h3>设计说明：</h3>
 * <ul>
 *   <li><b>OpenID</b> - 用户在某个具体应用下的唯一标识，同一用户在不同应用下 OpenID 不同</li>
 *   <li><b>UnionID</b> - 用户在同一开放平台下所有应用的唯一标识，同一用户在不同应用下 UnionID 相同</li>
 * </ul>
 *
 * <h3>生成规则：</h3>
 * <pre>
 * OpenID  = "o_" + MD5(platform + appId + userId + salt)        // 应用级别唯一
 * UnionID = "u_" + MD5(platform + platformKey + userId + salt)  // 平台级别唯一
 * </pre>
 *
 * <h3>生成规则图解：</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                        OpenID 生成规则                          │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  OpenID = "o_" + MD5(platform + appId + userId + salt)          │
 * │                                                                 │
 * │  特点：同一用户在不同应用下 OpenID 不同                          │
 * │                                                                 │
 * │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
 * │  │ 公众号 A    │  │ 小程序 B    │  │ APP C      │              │
 * │  │ appId: A001 │  │ appId: B001 │  │ appId: C001│              │
 * │  ├─────────────┤  ├─────────────┤  ├─────────────┤              │
 * │  │ o_abc111... │  │ o_def222... │  │ o_ghi333...│  ← 不同!     │
 * │  └─────────────┘  └─────────────┘  └─────────────┘              │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                       UnionID 生成规则                          │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  UnionID = "u_" + MD5(platform + platformKey + userId + salt)   │
 * │                                                                 │
 * │  特点：同一用户在同一平台下 UnionID 相同                         │
 * │                                                                 │
 * │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
 * │  │ 公众号 A    │  │ 小程序 B    │  │ APP C      │              │
 * │  │ platform:   │  │ platform:   │  │ platform:  │              │
 * │  │ wechat      │  │ wechat      │  │ wechat     │              │
 * │  ├─────────────┤  ├─────────────┤  ├─────────────┤              │
 * │  │ u_xyz999... │  │ u_xyz999... │  │ u_xyz999...│  ← 相同!     │
 * │  └─────────────┘  └─────────────┘  └─────────────┘              │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>格式说明：</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>格式</th><th>长度</th><th>示例</th></tr>
 *   <tr><td>OpenID</td><td>o_{32位MD5}</td><td>34字符</td><td>o_a1b2c3d4...</td></tr>
 *   <tr><td>UnionID</td><td>u_{32位MD5}</td><td>34字符</td><td>u_x1y2z3w4...</td></tr>
 * </table>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 微信小程序场景
 * String openId = OpenIdGenerator.generateOpenId("wechat", "wx123456", "user_001");
 * String unionId = OpenIdGenerator.generateUnionId("wechat", "open_platform_key", "user_001");
 *
 * // 自建系统场景
 * String openId = OpenIdGenerator.generateOpenId("system", "app_001", "user_001");
 * String unionId = OpenIdGenerator.generateUnionId("system", "company_key", "user_001");
 *
 * // 快捷方式
 * String[] ids = OpenIdGenerator.generateSystemIds("user_001");
 * }</pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-07
 */
public final class OpenIdGenerator {

    /**
     * OpenID 前缀
     */
    private static final String OPEN_ID_PREFIX = "o_";

    /**
     * UnionID 前缀
     */
    private static final String UNION_ID_PREFIX = "u_";

    /**
     * 默认盐值
     */
    private static final String DEFAULT_SALT = "oauth_salt_2025";

    private OpenIdGenerator() {
        // 工具类禁止实例化
    }

    /**
     * 生成 OpenID
     * <p>
     * 用户在某个具体应用下的唯一标识
     * 同一用户在不同应用（appId不同）下，OpenID 不同
     * </p>
     *
     * @param platform 平台标识（如：wechat, alipay, system）
     * @param appId    应用ID（如：wx123456, app_001）
     * @param userId   用户唯一标识（如：数据库主键、手机号等）
     * @return OpenID，格式：o_{32位MD5}
     */
    public static String generateOpenId(String platform, String appId, String userId) {
        if (StringUtils.isAnyBlank(platform, appId, userId)) {
            return null;
        }
        String source = platform + ":" + appId + ":" + userId + ":" + DEFAULT_SALT;
        return OPEN_ID_PREFIX + DigestUtils.md5Hex(source);
    }

    /**
     * 生成 UnionID
     * <p>
     * 用户在同一开放平台下所有应用的唯一标识
     * 同一用户在同一平台的不同应用下，UnionID 相同
     * </p>
     *
     * @param platform    平台标识（如：wechat, alipay, system）
     * @param platformKey 平台密钥（开放平台的唯一标识，同一开放平台下所有应用共享）
     * @param userId      用户唯一标识（如：数据库主键、手机号等）
     * @return UnionID，格式：u_{32位MD5}
     */
    public static String generateUnionId(String platform, String platformKey, String userId) {
        if (StringUtils.isAnyBlank(platform, platformKey, userId)) {
            return null;
        }
        String source = platform + ":" + platformKey + ":" + userId + ":" + DEFAULT_SALT;
        return UNION_ID_PREFIX + DigestUtils.md5Hex(source);
    }

    /**
     * 生成 OpenID（简化版）
     * <p>
     * 使用平台标识作为 appId
     * </p>
     *
     * @param platform 平台标识
     * @param userId   用户唯一标识
     * @return OpenID
     */
    public static String generateOpenId(String platform, String userId) {
        return generateOpenId(platform, platform, userId);
    }

    /**
     * 生成 UnionID（简化版）
     * <p>
     * 使用平台标识作为 platformKey
     * </p>
     *
     * @param platform 平台标识
     * @param userId   用户唯一标识
     * @return UnionID
     */
    public static String generateUnionId(String platform, String userId) {
        return generateUnionId(platform, platform, userId);
    }

    /**
     * 为系统内部用户生成 OpenID 和 UnionID
     * <p>
     * 适用于自建系统，非第三方登录场景
     * </p>
     *
     * @param userId 用户ID
     * @return 包含 openId 和 unionId 的数组 [openId, unionId]
     */
    public static String[] generateSystemIds(String userId) {
        return generateSystemIds("system", userId);
    }

    /**
     * 为系统内部用户生成 OpenID 和 UnionID
     *
     * @param appId  应用标识
     * @param userId 用户ID
     * @return 包含 openId 和 unionId 的数组 [openId, unionId]
     */
    public static String[] generateSystemIds(String appId, String userId) {
        String openId = generateOpenId("system", appId, userId);
        String unionId = generateUnionId("system", "default", userId);
        return new String[]{openId, unionId};
    }

    /**
     * 判断是否为有效的 OpenID
     *
     * @param openId OpenID
     * @return 是否有效
     */
    public static boolean isValidOpenId(String openId) {
        return StringUtils.isNotBlank(openId) && openId.startsWith(OPEN_ID_PREFIX) && openId.length() == 34;
    }

    /**
     * 判断是否为有效的 UnionID
     *
     * @param unionId UnionID
     * @return 是否有效
     */
    public static boolean isValidUnionId(String unionId) {
        return StringUtils.isNotBlank(unionId) && unionId.startsWith(UNION_ID_PREFIX) && unionId.length() == 34;
    }

    /**
     * 平台枚举
     */
    public static class Platform {
        /** 微信 */
        public static final String WECHAT = "wechat";
        /** 微信小程序 */
        public static final String WECHAT_MINI = "wechat_mini";
        /** 微信公众号 */
        public static final String WECHAT_MP = "wechat_mp";
        /** 支付宝 */
        public static final String ALIPAY = "alipay";
        /** 钉钉 */
        public static final String DINGTALK = "dingtalk";
        /** 企业微信 */
        public static final String WEWORK = "wework";
        /** GitHub */
        public static final String GITHUB = "github";
        /** Gitee */
        public static final String GITEE = "gitee";
        /** 系统内部 */
        public static final String SYSTEM = "system";

        private Platform() {
        }
    }
}
