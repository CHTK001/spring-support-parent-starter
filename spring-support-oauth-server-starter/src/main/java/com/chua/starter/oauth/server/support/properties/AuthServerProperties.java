package com.chua.starter.oauth.server.support.properties;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author CH
 * @since 2022/7/23 14:51
 */
@EqualsAndHashCode(callSuper = false)
@Data
@ConfigurationProperties(prefix = AuthServerProperties.PRE, ignoreUnknownFields = false)
public class AuthServerProperties {

    public static final String PRE = "plugin.oauth.server";
    /**
     * token-name
     */
    private String tokenName = "x-oauth-token";
    /**
     * cookie-name
     */
    private String cookieName = "x-oauth-cookie";

    /**
     * 加密方式
     */
    private String encryption = "aes";
    /**
     * 全局令牌超时时间(<=0 永不超时)(单位: s)
     */
    private long expire = 86_400L * 3;
    /**
     * token是否续费
     * <p>即每次认证超时时间重置</p>
     */
    private boolean renew;
    /**
     * 服务序列
     */
    private String serviceKey = "D518E462DF7B36828FA68CCD69FC6140";
    /**
     * 启动协议
     */
    private List<String> protocols = Lists.newArrayList("http");
    /**
     * 是否开启密钥校验
     */
    private boolean openCheckAkSk;
    /**
     * 是否开启回写加密
     */
    private boolean openReturnCheckAkSk = true;

    /**
     * 令牌管理, 用于管理token, cookie
     */
    private String tokenManagement = "redis";

    /**
     * 令牌生成, 用于生成ak, sk
     */
    private String tokenGeneration = "simple";
    /**
     * 日志实现
     */
    private String log = "simple";
    /**
     * ak, sk认证管理
     */
    private String authenticate = "";
    /**
     * 命名空间
     */
    private String contextPath;
    /**
     * 在线模式
     */
    private Online online = Online.MULTIPLE;

    /**
     * 用户信息校验模式
     */
    private Set<String> user = new HashSet<>();

    public enum Online {
        /**
         * 只允许一个在线
         */
        SINGLE,
        /**
         * 允许多个个在线
         */
        MULTIPLE
    }
}
