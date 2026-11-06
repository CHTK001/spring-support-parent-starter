package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;

import java.util.List;
import java.util.Map;

/**
 * Qoder 国内版（{@code qoder.cn}）账号渠道。
 *
 * <p>与国际版 {@link QoderAccountChannelAuthManager} 同一套协议，差异只有三处：
 * 授权站点、OpenAPI 站点、client_id。</p>
 *
 * <p><b>为什么单独建一个顶层类</b>：SPI 框架加载 {@code META-INF/extensions} 里的实现时，
 * 会为每个实现登记两条 definition：一条用 {@code @Spi} 声明名的别名（小写，如 {@code qoder-cn}），
 * 另一条用类简名（大写，如 {@code QoderCnAccountChannelAuthManager} → {@code QODERCN}）。
 * 两者指向同一个实例，因此渠道清单必须以实现自身的 {@code channel()} 去重
 * （见 {@code ApiChannelAuthController#listChannels()}），否则同一渠道会出现两张卡片。
 * 控制器这里的按大小写不敏感匹配则让 {@code qoder-cn} / {@code QODERCN} 都能路由到本实现。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Spi("qoder-cn")
public class QoderCnAccountChannelAuthManager extends QoderAccountChannelAuthManager {

    /** 渠道标识（与 {@code @Spi} 别名一致，服务端会转成大写 {@code QODER-CN}）。 */
    static final String CHANNEL = "qoder-cn";
    /** 授权页站点。 */
    static final String CN_AUTH_BASE = "https://qoder.cn";
    /** OpenAPI 站点。 */
    static final String CN_OPEN_API_BASE = "https://openapi.qoder.com.cn";
    /** 国内版 client_id。 */
    static final String CN_CLIENT_ID = "732aef47-9cf2-46a2-95fe-4cebb5d0d1fa";

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public String displayName() {
        return "Qoder（国内）";
    }

    @Override
    protected String authBase() {
        return CN_AUTH_BASE;
    }

    @Override
    protected String openApiBase() {
        return CN_OPEN_API_BASE;
    }

    @Override
    protected String clientId() {
        return CN_CLIENT_ID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>国内版在售模型与国际版不同，按 {@code dsh-our-free-model} 的国内清单返回。</p>
     */
    @Override
    public List<Map<String, Object>> listModels(Map<String, Object> credentials) {
        return List.of(
                model("dfmodel", "DeepSeek-Flash", 1_000_000, true),
                model("gm51model", "GLM-5.2", 1_000_000, true),
                model("kmodel_latest", "Kimi-K3", 200_000, true));
    }
}
