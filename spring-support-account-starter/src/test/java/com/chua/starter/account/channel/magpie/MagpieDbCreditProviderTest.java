// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.starter.common.support.channel.ChannelUpstreamProvider;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「我们自己配的余额来源」的回归测试。
 *
 * <p>两件事必须成立：</p>
 * <ul>
 *   <li>它在 {@code ChannelUpstreamProvider} 的登记里（少了这行，页面上的配置永远查不到）；</li>
 *   <li>没有配置、没有 Spring 上下文时返回 {@code null}「不接管」，而不是抛异常把额度卡带崩。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.82
 */
@DisplayName("magpie 自建余额来源：注册、快照与不接管")
class MagpieDbCreditProviderTest {

    /**
     * 登记表里必须有这个实现（走 META-INF/extensions 装载）。
     */
    @Test
    @DisplayName("注册：能被上游实现列表装载")
    void registeredAsUpstreamProvider() {
        List<ChannelUpstreamProvider> impls = MagpieUpstreamCreditSpi.providers();
        assertNotNull(impls);
        assertTrue(impls.stream().anyMatch(impl -> impl instanceof MagpieDbCreditProvider),
                "MagpieDbCreditProvider 未登记到 ChannelUpstreamProvider");
    }

    /**
     * 没有 Spring 上下文（单测直接 new）时不接管，也不抛异常。
     */
    @Test
    @DisplayName("不接管：无容器 / 无配置时返回 null")
    void returnsNullWithoutConfig() {
        MagpieDbCreditProvider provider = new MagpieDbCreditProvider();
        assertFalse(provider.supports("moxuna"));
        assertNull(provider.credit("moxuna", null, Map.of()));
    }

    /**
     * 读数要摊成 packages，页面每段一行。
     */
    @Test
    @DisplayName("快照：多段读数各占一行，币种保留")
    void snapshotListsEveryPart() {
        BalancePart first = part("account", "12.5", "USD");
        BalancePart second = part("voucher", "3", "USD");

        Map<String, Object> snapshot = MagpieDbCreditProvider.snapshot(
                CreditResult.ok("expression", List.of(first, second), "{}"));

        Object packages = snapshot.get("packages");
        assertTrue(packages instanceof List<?>);
        List<?> rows = (List<?>) packages;
        assertEquals(2, rows.size());
        assertEquals("account", ((Map<?, ?>) rows.get(0)).get("name"));
        assertEquals("voucher", ((Map<?, ?>) rows.get(1)).get("name"));
        assertEquals("USD", ((Map<?, ?>) rows.get(0)).get("currency"));
        assertNotNull(snapshot.get("updatedAt"));
        assertNull(snapshot.get("error"));
    }

    /**
     * 查不到时只给原因，不给空 packages。
     */
    @Test
    @DisplayName("失败：只回 error")
    void snapshotCarriesError() {
        Map<String, Object> failed = MagpieDbCreditProvider.snapshot(
                CreditResult.fail("expression", "HTTP 401"));
        assertEquals("HTTP 401", failed.get("error"));
        assertNull(failed.get("packages"));

        Map<String, Object> empty = MagpieDbCreditProvider.snapshot(null);
        assertNotNull(empty.get("error"));
    }

    /**
     * 造一段读数。
     *
     * @param label    段名
     * @param amount   金额
     * @param currency 币种
     * @return 读数
     */
    private static BalancePart part(String label, String amount, String currency) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(new BigDecimal(amount));
        part.setCurrency(currency);
        return part;
    }
}
