package com.chua.starter.datasearch.provider.limit;

import com.chua.starter.datasearch.provider.model.ProviderLimitInfo;
import com.chua.starter.datasearch.provider.support.ProviderLimits;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 官方限制登记表的适配层：把「数据侧」登记项翻成「机制侧」快照。
 *
 * <p>数据与匹配规则属于厂商定义，由 {@link com.chua.starter.datasearch.provider.spi.ProviderLimitProvider}
 * SPI 提供（资源 {@code provider-catalog/provider-limit-docs.json}，由 models.dev 目录与模板库生成）。
 * 本类只负责把 {@link ProviderLimitInfo} 翻成 {@link ProviderLimit} 并转成 {@link Entry}，
 * 供 {@link DocLimitMechanism} 与 {@link ProviderLimitCollector} 使用。</p>
 *
 * <p>新增数据源（厂商控制台、私有登记表）实现数据侧 SPI 并在 {@code META-INF/services}
 * 登记即可，本类不需要改动。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class ProviderLimitPresets {

    /**
     * 登记表口径的键名是下划线小写，与 {@code extra.limits_manual} 一致
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 工具类，禁止实例化。
     */
    private ProviderLimitPresets() {
    }

    /**
     * 一条登记项。
     *
     * @param id       厂商键
     * @param name     展示名
     * @param aliases  精确匹配的 provider id
     * @param hosts    主机名匹配
     * @param doc      官方文档入口（可为 null）
     * @param verified 数值 / 计费口径是否按官方原文核对过
     * @param limits   已核对的口径（下划线键，可空）
     * @param notes    补充说明
     */
    public record Entry(String id,
                        String name,
                        List<String> aliases,
                        List<String> hosts,
                        String doc,
                        boolean verified,
                        Map<String, Object> limits,
                        String notes) {

        /**
         * 转成限制快照；没有数值 / 计费口径时返回的对象 {@link ProviderLimit#hasValues()} 为 false。
         *
         * @return 限制快照
         */
        public ProviderLimit toLimit() {
            ProviderLimit limit = CollectionUtils.isEmpty(limits)
                    ? new ProviderLimit()
                    : MAPPER.convertValue(limits, ProviderLimit.class);
            limit.setKind("doc");
            limit.setSource(id);
            limit.setSourceUrl(doc);
            limit.setVerified(verified);
            limit.setNotes(notes);
            limit.setCapturedAt(System.currentTimeMillis());
            return limit;
        }
    }

    /**
     * 按平台 / 地址查「有可展示口径」的登记项。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 限制快照；未收录或只有文档入口时返回 null
     */
    public static ProviderLimit find(String platform, String baseUrl) {
        Entry entry = entryOf(platform, baseUrl);
        if (entry == null) {
            return null;
        }
        ProviderLimit limit = entry.toLimit();
        return limit.hasValues() ? limit : null;
    }

    /**
     * 按平台 / 地址查登记项（含只有文档入口的条目）。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 登记项；未收录返回 null
     */
    public static Entry entryOf(String platform, String baseUrl) {
        ProviderLimitInfo info = ProviderLimits.find(platform, baseUrl);
        return info == null ? null : toEntry(info);
    }

    /**
     * 官方文档入口。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 文档地址；未收录返回 null
     */
    public static String docUrl(String platform, String baseUrl) {
        return ProviderLimits.docUrl(platform, baseUrl);
    }

    /**
     * 命中的厂商键。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 厂商键；未收录返回 null
     */
    public static String docSource(String platform, String baseUrl) {
        return ProviderLimits.docSource(platform, baseUrl);
    }

    /**
     * 登记条目数。
     *
     * @return 条目数
     */
    public static int size() {
        return ProviderLimits.size();
    }

    /**
     * 全部登记项（只读）。
     *
     * @return 登记项
     */
    public static List<Entry> entries() {
        List<ProviderLimitInfo> infos = ProviderLimits.entries();
        if (infos.isEmpty()) {
            return Collections.emptyList();
        }
        List<Entry> entries = new ArrayList<>(infos.size());
        for (ProviderLimitInfo info : infos) {
            entries.add(toEntry(info));
        }
        return Collections.unmodifiableList(entries);
    }

    /**
     * 数据侧登记项 → 机制侧 Entry。
     *
     * @param info 登记项
     * @return Entry
     */
    private static Entry toEntry(ProviderLimitInfo info) {
        Map<String, Object> limits = info.getLimits() == null
                ? Collections.emptyMap() : info.getLimits();
        return new Entry(info.getId(), info.getName(),
                info.getAliases() == null ? Collections.emptyList() : info.getAliases(),
                info.getHosts() == null ? Collections.emptyList() : info.getHosts(),
                info.getDoc(), info.isVerified(), limits, info.getNotes());
    }
}
