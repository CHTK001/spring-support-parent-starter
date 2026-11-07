// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;

/**
 * Qoder 国内版（{@code qoder.cn}）积分 / 额度查询实现。
 *
 * <p>与国际版同一套协议，只换 OpenAPI 站点与渠道标识（对齐
 * {@link QoderCnAccountChannelAuthManager} 的差异点）。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("qoder-cn")
public class QoderCnChannelCreditProvider extends QoderChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "qoder-cn";

    /**
     * 国内版 OpenAPI 站点
     */
    static final String CN_OPEN_API_BASE = "https://openapi.qoder.com.cn";

    /**
     * 构造国内版实现。
     */
    public QoderCnChannelCreditProvider() {
        super(CHANNEL, CN_OPEN_API_BASE);
    }
}
