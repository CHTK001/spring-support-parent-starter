package com.chua.starter.common.support.api.sniff;

import com.chua.common.support.network.server.filter.security.BotSniffGuard;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 嗅探（bot 挑战）配置属性。
 *
 * <p>yaml 位置：{@code plugin.security.sniff.*}（与 {@code plugin.api.*}、{@code plugin.security.fingerprint.*} 同层）。
 * 装配口径与 {@code CodecRequestOpen} / {@code CodecTraceOpen} 一致：<b>缺省即装配</b>
 * （{@code matchIfMissing = true}），真正的开关是 {@code sys_setting}(group=config) 的
 * {@code CodecSniffOpen}，只把系统配置打开就生效、不用改 yaml。本类只提供
 * {@code enabled=false} 硬关与 include/exclude/ttl/密钥等调参。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "plugin.security.sniff")
public class BotSniffProperties {

    /**
     * 是否装配嗅探过滤器（默认装配；置 false 是硬关，等价于彻底不引入本能力）
     */
    private boolean enabled = true;

    /**
     * ticket 签名密钥（HMAC）；挑战解答的密文改用每次挑战的一次性随机数作密钥
     */
    private String secretKey = BotSniffGuard.DEFAULT_SECRET_KEY;

    /**
     * ticket 请求头名
     */
    private String ticketHeader = BotSniffGuard.DEFAULT_TICKET_HEADER;

    /**
     * 挑战解答请求头名
     */
    private String solvedHeader = BotSniffGuard.DEFAULT_SOLVED_HEADER;

    /**
     * ticket 有效期（秒）
     */
    private long ticketTtlSeconds = 1800L;

    /**
     * 需要嗅探的路径模式（支持 {@code *} 单段、{@code **} 任意深度）
     */
    private List<String> includePaths = new ArrayList<>(List.of("/v2/**", "/api/**"));

    /**
     * 豁免路径模式；为空时用引擎内置豁免（设置水合、登录、登出、验证码）
     */
    private List<String> excludePaths = new ArrayList<>();

    /**
     * 是否信任 {@code X-Forwarded-For} 作为客户端地址（默认不信任，防伪造日志来源）
     */
    private boolean trustForwardedHeader = false;
}
