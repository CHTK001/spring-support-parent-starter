// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;

/**
 * Qoder 国内版成长任务实现。
 *
 * <p>与国际版同一套活动协议，只换 OpenAPI 站点与渠道标识。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("qoder-cn")
public class QoderCnChannelTaskProvider extends QoderChannelTaskProvider {

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
    public QoderCnChannelTaskProvider() {
        super(CHANNEL, CN_OPEN_API_BASE);
    }
}
