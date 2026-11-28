package com.chua.starter.sso.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * SSO 安全配置属性
 * <p>
 * 第三方登录（SSO）相关配置：回调加密密钥与系统回调地址白名单。
 * 配置前缀与原 system 模块保持一致（{@code plugin.system.security}），宿主无需迁移配置。
 * </p>
 *
 * @since 2026/11/24
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = SsoProperties.PRE)
public class SsoProperties {

    /**
     * PRE
     */
    public static final String PRE = "plugin.system.security";

    /**
     * 第三方登录回调加密密钥
     * <p>用于加密第三方登录回调时传递的登录信息，密钥长度必须为16位</p>
     */
    private String thirdPartyCallbackKey = "1234567890Oil#@1";

    /**
     * 第三方登录「系统回调地址」白名单（{@code scheme://host[:port]} 前缀）
     *
     * <p>守护 {@code callback} 参数的开放重定向缺陷：回调地址为绝对地址时，
     * 其 {@code scheme://host[:port]} 前缀必须命中本列表，否则拒绝重定向
     * （见 {@code com.chua.starter.sso.support.SystemCallbackPolicy}）。</p>
     *
     * <p><b>默认空 = fail-closed</b>：绝对地址一律拒绝，仅允许站内相对路径
     * （以单个 {@code /} 开头）。前端发送的是「绝对 origin + pathname」，
     * 因此生产与开发环境<b>都必须</b>显式配置，否则三方登录回跳会失败，
     * 例如：</p>
     * <pre>
     * plugin:
     *   system:
     *     security:
     *       allowed-system-callback-hosts:
     *         - http://localhost:8096
     *         - http://192.168.50.198:8096
     * </pre>
     *
     * @see com.chua.starter.sso.support.SystemCallbackPolicy
     */
    private List<String> allowedSystemCallbackHosts = new ArrayList<>();
}
