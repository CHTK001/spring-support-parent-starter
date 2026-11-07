// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.common.support.channel.ChannelCreditProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WorkBuddy 积分查询实现。
 *
 * <p>上游两个口径（对齐 {@code workbuddy2api-panel} 的 {@code UserResource}）：</p>
 * <ul>
 *   <li>个人：{@code POST {billing}/v2/billing/meter/get-user-resource}
 *       → {@code Response.Data.Accounts[]}（周期额度优先，逐包累加）；</li>
 *   <li>企业：{@code POST {billing}/v2/billing/meter/get-enterprise-user-usage}
 *       → {@code credit}（本周期已用）+ {@code limitNum}（分配额度）。</li>
 * </ul>
 *
 * <p>签到与读数是两条独立通路：本类只读，绝不触发签到动作。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Spi("workbuddy")
public class WorkBuddyChannelCreditProvider implements ChannelCreditProvider {

    /**
     * 渠道标识
     */
    static final String CHANNEL = "workbuddy";

    /**
     * 具体实现（读积分与凭据解析都复用鉴权管理器，避免两处口径漂移）
     */
    private final WorkBuddyAccountChannelAuthManager delegate = new WorkBuddyAccountChannelAuthManager();

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public String creditUnit() {
        return "credit";
    }

    @Override
    public Map<String, Object> credit(Map<String, Object> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>(2);
            empty.put("error", "账号未绑定或凭据为空");
            return empty;
        }
        return delegate.credit(credentials);
    }
}
