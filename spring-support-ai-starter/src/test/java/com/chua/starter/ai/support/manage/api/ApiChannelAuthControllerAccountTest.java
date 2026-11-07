package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApiChannelAuthController} 账号管理相关的纯函数回归测试。
 *
 * <p>覆盖三处易错、且不会在类型检查里暴露的逻辑：</p>
 * <ul>
 *   <li>{@code expiresAtOf}：各 SPI 实现写凭据的键名/单位/格式都不一致
 *       （{@code expire_time} 毫秒、{@code expires_at} 毫秒、Gemini 的 {@code expiry}
 *       是 RFC3339 字符串），漏一个键就让对应渠道的「到期」永远为空；秒级时间戳不归一化
 *       会显示成 1970 年。</li>
 *   <li>{@code normalizeEpoch}：1e12 阈值两侧的行为。</li>
 *   <li>{@code channelOf}：凭据里没写 channel 时必须回落到 provider 所属渠道，
 *       否则前端按渠道分组会漏掉这些账号。</li>
 *   <li>{@code putCheckinInfo}：签到摘要是展示键，必须从凭据里单独取出并裁空白；
 *       缺字段时不写键，前端 {@code v-if} 才判得出「从没签到过」。</li>
 * </ul>
 *
 * <p>不做网络请求、不启动 Spring 上下文：直接反射调用私有静态方法。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
class ApiChannelAuthControllerAccountTest {

    /** 2026-11-02T10:00:00Z 的毫秒值，作为各键的期望结果 */
    private static final long EXPECTED_MS = Instant.parse("2026-11-02T10:00:00Z").toEpochMilli();

    @Test
    @DisplayName("expiresAtOf：expire_time（Minimax/Qoder/Raccoon 口径）毫秒直取")
    void expiresAtOfReadsExpireTimeMillis() throws Exception {
        assertEquals(EXPECTED_MS, expiresAtOf(connectionWith("expire_time", EXPECTED_MS)));
    }

    @Test
    @DisplayName("expiresAtOf：expires_at（Trae/Lobsterai/Loomy 口径）毫秒直取")
    void expiresAtOfReadsExpiresAtMillis() throws Exception {
        assertEquals(EXPECTED_MS, expiresAtOf(connectionWith("expires_at", EXPECTED_MS)));
    }

    @Test
    @DisplayName("expiresAtOf：expiry 为 RFC3339 字符串（Gemini 口径）解析为毫秒")
    void expiresAtOfParsesRfc3339Expiry() throws Exception {
        assertEquals(EXPECTED_MS, expiresAtOf(connectionWith("expiry", "2026-11-02T10:00:00Z")));
        // 带偏移量的写法同样要能解析
        assertEquals(EXPECTED_MS, expiresAtOf(connectionWith("expiry", "2026-11-02T18:00:00+08:00")));
    }

    @Test
    @DisplayName("expiresAtOf：10 位秒级时间戳归一化成毫秒（否则前端算成 1970 年）")
    void expiresAtOfNormalizesSecondsToMillis() throws Exception {
        long seconds = EXPECTED_MS / 1000L;
        assertEquals(EXPECTED_MS, expiresAtOf(connectionWith("expires_at", seconds)));
        // 字符串形态的秒级时间戳（JacksonTypeHandler 反序列化后可能是 String）
        assertEquals(EXPECTED_MS, expiresAtOf(connectionWith("expires_at", String.valueOf(seconds))));
    }

    @Test
    @DisplayName("expiresAtOf：无凭据 / 无到期键 / 非法值一律返回 null（前端显示「—」而非已过期）")
    void expiresAtOfReturnsNullWhenUnknown() throws Exception {
        assertNull(expiresAtOf(null), "空连接应为 null");
        assertNull(expiresAtOf(connectionWith("access_token", "abc")), "无到期键应为 null");
        assertNull(expiresAtOf(connectionWith("expires_at", "not-a-date")), "无法解析应为 null");
        assertNull(expiresAtOf(connectionWith("expires_at", 0L)), "0 不应被当成已过期");
        assertNull(expiresAtOf(connectionWith("expires_at", -1L)), "负值应为 null");
    }

    @Test
    @DisplayName("channelOf：凭据内 channel 优先，缺失时回落到 provider 所属渠道")
    void channelOfFallsBackToProviderChannel() throws Exception {
        assertEquals("qoder", channelOf(connectionWith("channel", "qoder"), "QODER"));
        assertEquals("QODER", channelOf(connectionWith("access_token", "x"), "QODER"));
        assertEquals("QODER", channelOf(null, "QODER"));
        // 空串 / 空白也算缺失，不能当前端分组的 key
        assertEquals("QODER", channelOf(connectionWith("channel", "  "), "QODER"));
    }

    @Test
    @DisplayName("putCheckinInfo：签到摘要单独回给前端，缺字段时不写键")
    void putCheckinInfoExposesCheckinSummary() throws Exception {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("checkin_at", EXPECTED_MS);
        credentials.put("checkin_message", "workbuddy 处理 1 个账号");
        credentials.put("access_token", "secret");

        Map<String, Object> summary = new LinkedHashMap<>();
        putCheckinInfo(summary, connectionWithCredentials(credentials));
        assertEquals(EXPECTED_MS, summary.get("checkinAt"));
        assertEquals("workbuddy 处理 1 个账号", summary.get("checkinMessage"));
        // 只回展示键：凭据原文一律不进摘要
        assertEquals(2, summary.size(), "摘要里不能混入 access_token 等凭据键");
    }

    @Test
    @DisplayName("putCheckinInfo：只有消息时不写 checkinAt，空白消息与空凭据一律不加键")
    void putCheckinInfoSkipsMissingParts() throws Exception {
        Map<String, Object> onlyMessage = new LinkedHashMap<>();
        onlyMessage.put("checkin_message", " 网关里没有支持签到的上游账号 ");
        Map<String, Object> two = new LinkedHashMap<>();
        putCheckinInfo(two, connectionWithCredentials(onlyMessage));
        assertEquals("网关里没有支持签到的上游账号", two.get("checkinMessage"), "首尾空白要裁掉");
        assertFalse(two.containsKey("checkinAt"), "没有 checkin_at 就不该写键");

        Map<String, Object> blankMessage = new LinkedHashMap<>();
        blankMessage.put("checkin_message", "   ");
        Map<String, Object> blank = new LinkedHashMap<>();
        putCheckinInfo(blank, connectionWithCredentials(blankMessage));
        assertTrue(blank.isEmpty(), "空白消息不应占位");

        Map<String, Object> none = new LinkedHashMap<>();
        putCheckinInfo(none, null);
        putCheckinInfo(none, new SysAiProviderConnection());
        assertTrue(none.isEmpty(), "无凭据不算签到过");
    }

    // ---------- 反射helper ----------

    private static SysAiProviderConnection connectionWithCredentials(Map<String, Object> credentials) {
        SysAiProviderConnection c = new SysAiProviderConnection();
        c.setCredentials(credentials);
        return c;
    }

    /** putCheckinInfo 方法 */
    private static void putCheckinInfo(Map<String, Object> cm, SysAiProviderConnection c) throws Exception {
        Method m = ApiChannelAuthController.class.getDeclaredMethod(
                "putCheckinInfo", Map.class, SysAiProviderConnection.class);
        m.setAccessible(true);
        m.invoke(null, cm, c);
    }

    /** connectionWith 方法 */
    private static SysAiProviderConnection connectionWith(String key, Object value) {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put(key, value);
        SysAiProviderConnection c = new SysAiProviderConnection();
        c.setCredentials(credentials);
        return c;
    }

    /** expiresAtOf 方法 */
    private static Long expiresAtOf(SysAiProviderConnection c) throws Exception {
        Method m = ApiChannelAuthController.class.getDeclaredMethod("expiresAtOf", SysAiProviderConnection.class);
        m.setAccessible(true);
        return (Long) m.invoke(null, c);
    }

    /** channelOf 方法 */
    private static String channelOf(SysAiProviderConnection c, String fallback) throws Exception {
        Method m = ApiChannelAuthController.class.getDeclaredMethod(
                "channelOf", SysAiProviderConnection.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, c, fallback);
    }
}
