// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.channel.magpie;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.channel.ChannelUpstreamProvider;
import com.chua.starter.common.support.channel.MagpieSupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * magpie 上游的「自建余额」回落点。
 *
 * <p>网关里有些上游没有余额接口（SiliconFlow 已下线 {@code /v1/user/info}），
 * 额度卡上就只剩一句「读不到」。宿主可以按 provider 实现
 * {@link ChannelUpstreamProvider#credit(String, String, Map)} 接自己的余额源，
 * 本类负责把这些实现串起来：</p>
 * <ul>
 *   <li><b>只在网关读不到时采用</b>：调用方已经确认这个上游没有可用读数才进来，
 *       网关有数字时不会问自建源，避免两套数字打架；</li>
 *   <li><b>第一个非 {@code null} 的实现胜出</b>：返回 {@code null} 表示「不接管」，
 *       继续问下一个；这样通用实现（如 {@code MagpieUpstreamProvider}，
 *       {@code supports} 默认 true）不会把专门实现挤掉；</li>
 *   <li><b>不按 SPI 别名取</b>：注册别名必须唯一（同名的两个实现会被 SPI 容器只留一个），
 *       所以这里遍历 {@code list().values()} 后按 {@code channel()} + {@code supports(providerId)}
 *       过滤，并用 identity 去重（同一个实现会以别名与类简名登记两次）。</li>
 * </ul>
 *
 * <p><b>容错约定</b>：自建实现抛异常只当作「不接管」，不向上传播——余额是展示性信息，
 * 自建源挂了不该把整个渠道页打红。</p>
 *
 * @author CH
 * @since 4.1.0
 */
final class MagpieUpstreamCreditSpi {

    /**
     * 上游接口未给单位时的展示名。
     */
    private static final String UNIT_CREDIT_LABEL = "积分";

    /**
     * 单位：Token。
     */
    private static final String UNIT_TOKEN = "token";

    /**
     * 单位：货币。
     */
    private static final String UNIT_CURRENCY = "currency";

    /**
     * 单项余额的名字（没有名字时的兜底）。
     */
    private static final String DEFAULT_NAME = "余额";

    /**
     * 已加载的实现列表（SPI 容器内部会缓存，这里只缓存一次遍历结果）。
     */
    private static volatile List<ChannelUpstreamProvider> cached;

    private MagpieUpstreamCreditSpi() {
    }

    /**
     * 当前可用的上游实现列表（按 SPI 注册顺序，已去重）。
     *
     * <p>包内可见是为了单测与排障：真实调用走本方法，测试直接传自己的列表。</p>
     *
     * @return 实现列表；没有任何实现时返回空列表
     */
    static List<ChannelUpstreamProvider> providers() {
        List<ChannelUpstreamProvider> local = cached;
        if (local != null) {
            return local;
        }
        synchronized (MagpieUpstreamCreditSpi.class) {
            local = cached;
            if (local == null) {
                // 同一个实现会以「@Spi 别名」和「类简名」登记两次，必须按实例去重，
                // 否则会重复询问同一个源（有网络调用时就是两倍耗时）。
                local = new ArrayList<>(new LinkedHashSet<>(
                        ServiceProvider.of(ChannelUpstreamProvider.class).list().values()));
                cached = local;
            }
        }
        return local;
    }

    /**
     * 问自建余额源要这个上游的读数。
     *
     * @param providerId  网关上游 id
     * @param account     上游账号（可空）
     * @param credentials 网关连接凭据（只读）
     * @return 第一个接管的快照；都不接管返回 {@code null}
     */
    static Map<String, Object> credit(String providerId, String account, Map<String, Object> credentials) {
        return credit(providers(), providerId, account, credentials);
    }

    /**
     * 在给定实现列表里找第一个接管的读数（供单测直接注入）。
     *
     * @param impls       候选实现
     * @param providerId  网关上游 id
     * @param account     上游账号（可空）
     * @param credentials 网关连接凭据（只读）
     * @return 第一个非 {@code null} 的快照；都不接管返回 {@code null}
     */
    static Map<String, Object> credit(List<ChannelUpstreamProvider> impls, String providerId,
                                      String account, Map<String, Object> credentials) {
        if (MagpieSupport.isBlank(providerId) || impls == null || impls.isEmpty()) {
            return null;
        }
        String wanted = providerId.trim();
        for (ChannelUpstreamProvider impl : impls) {
            if (impl == null || !MagpieSupport.CHANNEL.equalsIgnoreCase(impl.channel())) {
                continue;
            }
            if (!supportsQuietly(impl, wanted)) {
                continue;
            }
            Map<String, Object> snapshot;
            try {
                snapshot = impl.credit(wanted, account, credentials);
            } catch (RuntimeException ignored) {
                // 自建源自己挂了：按「不接管」处理，让网关的原因继续显示
                continue;
            }
            if (snapshot != null) {
                return snapshot;
            }
        }
        return null;
    }

    /**
     * 把自建读音转成额度卡的窗口列表（账号卡按这个渲染）。
     *
     * <p>优先用 {@code packages}（每项有名字，多币种 / 多钱包能各占一行）；
     * 没有明细时用顶层 {@code remain/total} 合成一行；再没有就退回 {@code text} 原文。</p>
     *
     * @param snapshot 自建读数
     * @return 窗口列表；没有可读内容返回空列表
     */
    static List<Map<String, Object>> windowsOf(Map<String, Object> snapshot) {
        List<Map<String, Object>> windows = new ArrayList<>(2);
        if (snapshot == null || snapshot.isEmpty()) {
            return windows;
        }
        Object packages = snapshot.get("packages");
        if (packages instanceof List<?> rows) {
            for (Object item : rows) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> row = stringKeyed(raw);
                String name = text(row.get("name"));
                String display = text(row.get("display"));
                if (display.isEmpty()) {
                    display = amountText(row.get("remain"), row.get("size"),
                            text(row.get("unit")), text(row.get("currency")));
                }
                if (display.isEmpty()) {
                    continue;
                }
                Map<String, Object> window = new LinkedHashMap<>(4);
                window.put("name", name.isEmpty() ? DEFAULT_NAME : name);
                window.put("display", display);
                long resetsAt = epoch(row.get("expiresAt"));
                if (resetsAt > 0) {
                    window.put("resetsAt", resetsAt);
                }
                windows.add(window);
            }
        }
        if (!windows.isEmpty()) {
            return windows;
        }
        String display = amountText(snapshot.get("remain"), snapshot.get("total"),
                text(snapshot.get("unit")), text(snapshot.get("currency")));
        if (display.isEmpty()) {
            display = text(snapshot.get("text"));
        }
        if (!display.isEmpty()) {
            Map<String, Object> window = new LinkedHashMap<>(3);
            window.put("name", DEFAULT_NAME);
            window.put("display", display);
            windows.add(window);
        }
        return windows;
    }

    /**
     * 自建读数的时间戳（额度卡的 {@code readAt} 要 ISO 串，前端口径按 ISO 解析）。
     *
     * @param snapshot 自建读数
     * @return ISO 时间；没有可用时间时取当前时刻
     */
    static String readAtOf(Map<String, Object> snapshot) {
        long value = snapshot == null ? 0L : epoch(snapshot.get("updatedAt"));
        return Instant.ofEpochMilli(value > 0 ? value : System.currentTimeMillis()).toString();
    }

    /**
     * 读取字符串字段。
     *
     * @param value 原始值
     * @return 字符串；空返回空串
     */
    static String text(Object value) {
        if (value instanceof String string) {
            return string.trim();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "";
    }

    /**
     * 询问 {@code supports} 时不因实现自身异常而中断整条链。
     *
     * @param impl       实现
     * @param providerId 上游 id
     * @return true 表示由该实现处理
     */
    private static boolean supportsQuietly(ChannelUpstreamProvider impl, String providerId) {
        try {
            return impl.supports(providerId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * 把读数拼成「12 / 100 积分」这种一行文案。
     *
     * @param remain   剩余（可空）
     * @param total    总量（可空）
     * @param unit     单位（可空）
     * @param currency 币种（可空）
     * @return 展示文案；剩余缺失返回空串
     */
    private static String amountText(Object remain, Object total, String unit, String currency) {
        String amount = numberText(remain);
        if (amount.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder(amount);
        String limit = numberText(total);
        if (!limit.isEmpty()) {
            text.append(" / ").append(limit);
        }
        String suffix = unitSuffix(unit, currency);
        if (!suffix.isEmpty()) {
            text.append(' ').append(suffix);
        }
        return text.toString();
    }

    /**
     * 数字转展示文本（去掉无意义的小数尾巴）。
     *
     * @param value 原始值
     * @return 展示文本；不是数字返回空串
     */
    private static String numberText(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return BigDecimal.valueOf(Double.parseDouble(string.trim()))
                        .stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
                return "";
            }
        }
        return "";
    }

    /**
     * 单位后缀：货币优先写币种，积分 / Token 写中文名。
     *
     * @param unit     单位
     * @param currency 币种
     * @return 后缀；无法判断返回空串
     */
    private static String unitSuffix(String unit, String currency) {
        if (!currency.isEmpty()) {
            return currency.toUpperCase(java.util.Locale.ROOT);
        }
        String normalized = unit.toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty() || "credit".equals(normalized) || "credits".equals(normalized)) {
            return UNIT_CREDIT_LABEL;
        }
        if (UNIT_TOKEN.equals(normalized)) {
            return "Token";
        }
        return UNIT_CURRENCY.equals(normalized) ? "" : unit;
    }

    /**
     * 时间归一：数值按秒 / 毫秒判定，ISO 串走 {@code Instant}。
     *
     * @param value 原始值
     * @return 毫秒时间戳；不可解析返回 0
     */
    private static long epoch(Object value) {
        if (value instanceof Number number) {
            long raw = number.longValue();
            return raw <= 0 ? 0L : (raw < 1_000_000_000_000L ? raw * 1000L : raw);
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Instant.parse(string.trim()).toEpochMilli();
            } catch (RuntimeException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    /**
     * Map 键统一成字符串（上游实现可能用 {@code Map<?,?>} 交回）。
     *
     * @param raw 原始 Map
     * @return 字符串键的 Map
     */
    private static Map<String, Object> stringKeyed(Map<?, ?> raw) {
        Map<String, Object> row = new LinkedHashMap<>(raw.size());
        raw.forEach((key, value) -> {
            if (key != null) {
                row.put(String.valueOf(key), value);
            }
        });
        return row;
    }
}
