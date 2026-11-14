// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.starter.common.support.channel.ChannelUpstreamProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自建余额回落（{@link MagpieUpstreamCreditSpi}）的测试。
 *
 * <p>守住四条口径：</p>
 * <ol>
 *   <li>返回 {@code null} 表示「不接管」，后面的实现仍会被询问；</li>
 *   <li>实现抛异常只当不接管，不能让页面报错；</li>
 *   <li>网关有读数时不调用自建源，两套数字不能混；</li>
 *   <li>自建读数填进同一张额度卡，去掉 {@code error}、标 {@code source=spi}。</li>
 * </ol>
 *
 * @author CH
 * @since 4.1.0
 */
class MagpieUpstreamCreditSpiTest {

    /**
     * 上游 id：SiliconFlow（余额接口已下线的那家）。
     */
    private static final String PROVIDER = "siliconflow";

    @Test
    @DisplayName("返回 null 表示不接管，继续问下一个实现")
    void nullMeansNotMine() {
        FakeProvider silent = new FakeProvider(null, null);
        FakeProvider owner = new FakeProvider(Map.of("remain", 12), null);

        Map<String, Object> result = MagpieUpstreamCreditSpi.credit(
                List.of(silent, owner), PROVIDER, null, Map.of());

        assertEquals(1, silent.calls);
        assertEquals(1, owner.calls);
        assertEquals(12, result.get("remain"));
    }

    @Test
    @DisplayName("实现抛异常只当不接管，不影响下一个实现")
    void exceptionIsNotFatal() {
        FakeProvider broken = new FakeProvider(null, null, true);
        FakeProvider owner = new FakeProvider(Map.of("remain", 7), null);

        Map<String, Object> result = MagpieUpstreamCreditSpi.credit(
                List.of(broken, owner), PROVIDER, null, Map.of());

        assertEquals(7, result.get("remain"));
        assertNull(MagpieUpstreamCreditSpi.credit(List.of(broken), PROVIDER, null, Map.of()));
    }

    @Test
    @DisplayName("别的渠道 / supports=false 的实现不参与")
    void channelAndSupportAreRespected() {
        FakeProvider otherChannel = new FakeProvider(Map.of("remain", 1), null, false, "other");
        FakeProvider notMine = new FakeProvider(Map.of("remain", 2), Set.of("qoder"));

        assertNull(MagpieUpstreamCreditSpi.credit(
                List.of(otherChannel, notMine), PROVIDER, null, Map.of()));
    }

    @Test
    @DisplayName("明细按 packages 逐行给名字与读数")
    void windowsFromPackages() {
        List<Map<String, Object>> packages = new ArrayList<>();
        packages.add(row("钱包", 12.5, "currency", "usd", null));
        packages.add(row("赠送", 3, "credit", null, "3 积分"));

        List<Map<String, Object>> windows = MagpieUpstreamCreditSpi.windowsOf(
                Map.of("packages", packages));

        assertEquals(2, windows.size());
        assertEquals("钱包", windows.get(0).get("name"));
        assertEquals("12.5 USD", windows.get(0).get("display"));
        assertEquals("3 积分", windows.get(1).get("display"));
    }

    @Test
    @DisplayName("没有明细时用顶层 remain / total 合成一行")
    void windowsFromTopLevel() {
        List<Map<String, Object>> windows = MagpieUpstreamCreditSpi.windowsOf(
                Map.of("remain", 12, "total", 100, "unit", "credit"));

        assertEquals(1, windows.size());
        assertEquals("12 / 100 积分", windows.get(0).get("display"));
    }

    @Test
    @DisplayName("只有文案时（0 credits left）照原文显示")
    void windowsFromText() {
        List<Map<String, Object>> windows = MagpieUpstreamCreditSpi.windowsOf(
                Map.of("text", "0 credits left"));

        assertEquals(1, windows.size());
        assertEquals("0 credits left", windows.get(0).get("display"));
    }

    @Test
    @DisplayName("网关有读数时不问自建源")
    void gatewayReadingWins() {
        FakeProvider spi = new FakeProvider(Map.of("remain", 999), null);
        List<Map<String, Object>> quotas = List.of(quota("main", 5));

        Map<String, Object> result = MagpieChannelCreditProvider.resolve(
                quotas, List.of(spi), PROVIDER, PROVIDER, null, Map.of());

        assertEquals(0, spi.calls);
        assertEquals(5L, result.get("remain"));
        assertNull(result.get("source"));
    }

    @Test
    @DisplayName("网关没有这个上游的卡时才走自建源，并标 source=spi")
    void gatewayEmptyFallsBackToSpi() {
        FakeProvider spi = new FakeProvider(Map.of("remain", 42, "unit", "credit"), null);

        Map<String, Object> result = MagpieChannelCreditProvider.resolve(
                List.of(), List.of(spi), PROVIDER, PROVIDER, null, Map.of());

        assertEquals(1, spi.calls);
        assertEquals(42, result.get("remain"));
        assertEquals("spi", result.get("source"));
    }

    @Test
    @DisplayName("自建源也不接管时保留网关的「读不到」原因")
    void gatewayErrorKeptWhenSpiSilent() {
        Map<String, Object> result = MagpieChannelCreditProvider.resolve(
                List.of(), List.of(), PROVIDER, PROVIDER, null, Map.of());

        assertNotNull(result.get("error"));
        assertTrue(String.valueOf(result.get("error")).contains(PROVIDER));
        assertNull(result.get("source"));
    }

    @Test
    @DisplayName("自建读数填进同一张卡：去 error、留 seeAt、标 source")
    void fillsGatewayCard() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("provider", PROVIDER);
        card.put("name", "SiliconFlow");
        card.put("windows", List.of());
        card.put("error", "SiliconFlow retired its balance query (/v1/user/info)");
        card.put("seeAt", "https://cloud.siliconflow.cn");
        FakeProvider spi = new FakeProvider(Map.of("remain", 8, "unit", "currency", "currency", "cny"), null);

        List<Map<String, Object>> out = MagpieUpstreamAdmin.withCustomCredit(
                new ArrayList<>(List.of(card)), PROVIDER, null, Map.of(), List.of(spi));

        assertEquals(1, out.size());
        Map<String, Object> filled = out.get(0);
        assertNull(filled.get("error"));
        assertEquals("spi", filled.get("source"));
        assertEquals("https://cloud.siliconflow.cn", filled.get("seeAt"));
        assertEquals("8 CNY", windows(filled).get(0).get("display"));
    }

    @Test
    @DisplayName("网关有窗口时不问自建源，卡原样返回")
    void readableCardUntouched() {
        List<Map<String, Object>> quotas = new ArrayList<>(List.of(quota("main", 5)));
        FakeProvider spi = new FakeProvider(Map.of("remain", 999), null);

        List<Map<String, Object>> out = MagpieUpstreamAdmin.withCustomCredit(
                quotas, PROVIDER, null, Map.of(), List.of(spi));

        assertSame(quotas, out);
        assertEquals(0, spi.calls);
    }

    @Test
    @DisplayName("网关没有卡时补一张自建卡")
    void appendsWhenCardMissing() {
        FakeProvider spi = new FakeProvider(Map.of("remain", 3, "total", 10, "unit", "credit"), null);

        List<Map<String, Object>> out = MagpieUpstreamAdmin.withCustomCredit(
                new ArrayList<>(), PROVIDER, null, Map.of(), List.of(spi));

        assertEquals(1, out.size());
        assertEquals(PROVIDER, out.get(0).get("provider"));
        assertEquals("spi", out.get(0).get("source"));
        assertEquals("3 / 10 积分", windows(out.get(0)).get(0).get("display"));
    }

    @Test
    @DisplayName("自建源只给原因且网关无卡时，补一条失败说明")
    void appendsSpiFailure() {
        FakeProvider spi = new FakeProvider(Map.of("error", "自建余额服务超时"), null);

        List<Map<String, Object>> out = MagpieUpstreamAdmin.withCustomCredit(
                new ArrayList<>(), PROVIDER, null, Map.of(), List.of(spi));

        assertEquals(1, out.size());
        assertEquals("自建余额服务超时", out.get(0).get("error"));
        assertEquals("spi", out.get(0).get("source"));
    }

    @Test
    @DisplayName("全量额度列表（没点名上游）不逐家代查")
    void allProvidersQueryIsUntouched() {
        List<Map<String, Object>> quotas = new ArrayList<>();
        FakeProvider spi = new FakeProvider(Map.of("remain", 1), null);

        List<Map<String, Object>> out = MagpieUpstreamAdmin.withCustomCredit(
                quotas, null, null, Map.of(), List.of(spi));

        assertSame(quotas, out);
        assertEquals(0, spi.calls);
    }

    @Test
    @DisplayName("占位实现返回空 Map 时给出原因，而不是「尚未查询」")
    void emptySnapshotReportsReason() {
        Map<String, Object> result = MagpieChannelCreditProvider.spiSnapshot(Map.of());

        assertEquals("spi", result.get("source"));
        assertNotNull(result.get("error"));
        assertFalse(String.valueOf(result.get("error")).isBlank());
    }

    /**
     * 构造额度卡。
     *
     * @param windowName 窗口名
     * @param remain     剩余
     * @return 额度卡
     */
    private static Map<String, Object> quota(String windowName, long remain) {
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("name", windowName);
        window.put("balance", remain);
        window.put("limit", remain);
        window.put("unit", "credits");
        Map<String, Object> quota = new LinkedHashMap<>();
        quota.put("provider", PROVIDER);
        quota.put("name", PROVIDER);
        quota.put("kind", "subscription");
        quota.put("windows", List.of(window));
        return quota;
    }

    /**
     * 构造 packages 行。
     *
     * @param name     名字
     * @param remain   剩余
     * @param unit     单位
     * @param currency 币种
     * @param display  原文
     * @return 行
     */
    private static Map<String, Object> row(String name, double remain, String unit, String currency,
                                           String display) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("remain", remain);
        row.put("unit", unit);
        if (currency != null) {
            row.put("currency", currency);
        }
        if (display != null) {
            row.put("display", display);
        }
        return row;
    }

    /**
     * 取卡上的窗口列表。
     *
     * @param card 额度卡
     * @return 窗口列表
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> windows(Map<String, Object> card) {
        return (List<Map<String, Object>>) card.get("windows");
    }

    /**
     * 假的自建余额实现。
     */
    private static final class FakeProvider implements ChannelUpstreamProvider {

        /**
         * 要返回的快照；null 表示不接管。
         */
        private final Map<String, Object> snapshot;

        /**
         * 只处理这些上游；null 表示全部。
         */
        private final Set<String> supported;

        /**
         * 是否抛异常。
         */
        private final boolean broken;

        /**
         * 所属渠道。
         */
        private final String channel;

        /**
         * 被询问次数。
         */
        private int calls;

        /**
         * 构造。
         *
         * @param snapshot 返回的快照
         * @param supported 支持的上游
         */
        FakeProvider(Map<String, Object> snapshot, Set<String> supported) {
            this(snapshot, supported, false, MagpieSupportChannel.NAME);
        }

        /**
         * 构造。
         *
         * @param snapshot  返回的快照
         * @param supported 支持的上游
         * @param broken    是否抛异常
         */
        FakeProvider(Map<String, Object> snapshot, Set<String> supported, boolean broken) {
            this(snapshot, supported, broken, MagpieSupportChannel.NAME);
        }

        /**
         * 构造。
         *
         * @param snapshot  返回的快照
         * @param supported 支持的上游
         * @param broken    是否抛异常
         * @param channel   所属渠道
         */
        FakeProvider(Map<String, Object> snapshot, Set<String> supported, boolean broken, String channel) {
            this.snapshot = snapshot;
            this.supported = supported;
            this.broken = broken;
            this.channel = channel;
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public boolean supports(String providerId) {
            return supported == null || supported.contains(providerId);
        }

        @Override
        public Map<String, Object> credit(String providerId, String account,
                                          Map<String, Object> credentials) {
            calls++;
            if (broken) {
                throw new IllegalStateException("自建余额源炸了");
            }
            return snapshot;
        }
    }

    /**
     * 渠道名常量（避免在测试里散落字面量）。
     */
    private static final class MagpieSupportChannel {

        /**
         * magpie 渠道标识。
         */
        private static final String NAME = "magpie";

        private MagpieSupportChannel() {
        }
    }
}
