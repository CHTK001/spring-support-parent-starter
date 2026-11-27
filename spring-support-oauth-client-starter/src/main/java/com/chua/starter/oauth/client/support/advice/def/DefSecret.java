package com.chua.starter.oauth.client.support.advice.def;

import com.chua.common.support.utils.StringUtils;

/**
 * 默认凭据占位（已废弃）。
 *
 * <p>安全规约：源码中不得硬编码真实密钥。历史版本曾在源码中携带真实密钥，
 * 该密钥应视为已泄露并完成轮换。现改为从环境变量 / 系统属性读取，未配置时返回空串。</p>
 *
 * @author CH
 * @since 4.0.0
 * @deprecated 请改用配置中心或环境变量注入凭据，本类不再持有真实密钥。
 */
@Deprecated
public class DefSecret {

    /**
     * Access Key 的环境变量 / 系统属性名
     */
    public static final String ENV_ACCESS_KEY = "OAUTH_CLIENT_ACCESS_KEY";
    /**
     * Secret Key 的环境变量 / 系统属性名
     */
    public static final String ENV_SECRET_KEY = "OAUTH_CLIENT_SECRET_KEY";

    /**
     * Access_Key（自环境变量 / 系统属性读取，未配置时为空串）
     */
    public static final String ACCESS_KEY = env(ENV_ACCESS_KEY);
    /**
     * Secret_Key（自环境变量 / 系统属性读取，未配置时为空串）
     */
    public static final String SECRET_KEY = env(ENV_SECRET_KEY);

    /**
     * 读取环境变量，缺失时回退系统属性。
     *
     * @param name 变量名
     * @return 值，未配置返回空串
     */
    private static String env(String name) {
        String v = System.getenv(name);
        if (StringUtils.isEmpty(v)) {
            v = System.getProperty(name);
        }
        return v == null ? "" : v;
    }
}
