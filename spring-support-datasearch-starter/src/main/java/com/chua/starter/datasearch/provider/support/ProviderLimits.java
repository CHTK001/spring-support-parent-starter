package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderLimitInfo;
import com.chua.starter.datasearch.provider.spi.ProviderLimitProvider;
import com.chua.starter.datasearch.support.SpiProviders;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 限制查询出口：按顺序问各个 {@link ProviderLimitProvider}，命中结果<b>合并</b>。
 *
 * <p>与余额查询同构：实现全部走 {@code META-INF/services} 登记，这里只做装载缓存、
 * 选择与合并，不承载任何厂商口径。</p>
 *
 * <p>合并而不是「第一个命中算数」的原因：限制口径常被拆开登记——文档入口在登记表、
 * 数值来自官方文档抓取、备注来自人工核对。第一个命中的实现往往只有文档没有数值，
 * 直接返回会让「限制」列长期空白。合并时同一键以先到的为准，后续实现只补空；
 * {@code verified} 取或（任一来源按官方原文核对过即算核对过），
 * {@code aliases} / {@code hosts} 取并集。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Slf4j
public final class ProviderLimits {

    private static volatile List<ProviderLimitProvider> cached;

    /**
     * 工具类，禁止实例化。
     */
    private ProviderLimits() {
    }

    /**
     * 全部实现（按登记顺序）。
     *
     * @return 实现列表；装载失败时为空列表
     */
    public static List<ProviderLimitProvider> providers() {
        List<ProviderLimitProvider> current = cached;
        if (current == null || current.isEmpty()) {
            current = SpiProviders.list(ProviderLimitProvider.class);
            cached = current;
        }
        return current;
    }

    /**
     * 按平台查登记项（不要求上游地址）。
     *
     * <p>免登录渠道没有 {@code baseUrl}，只能按厂商标识 / 别名查；本重载就是给这类
     * 调用方用的。</p>
     *
     * @param platform 平台类型
     * @return 登记项；未命中返回 null
     */
    public static ProviderLimitInfo find(String platform) {
        return find(platform, null);
    }

    /**
     * 按平台 / 地址查登记项（各实现合并）。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址，可为 null（只按厂商标识匹配）
     * @return 登记项；未命中返回 null
     */
    public static ProviderLimitInfo find(String platform, String baseUrl) {
        ProviderLimitInfo merged = null;
        for (ProviderLimitProvider provider : providers()) {
            ProviderLimitInfo info;
            try {
                info = provider.query(platform, baseUrl);
            } catch (Exception e) {
                log.debug("[provider-limit] 实现[{}]查询失败 platform={} cause={}",
                        provider.name(), platform, e.getMessage());
                continue;
            }
            if (info != null) {
                merged = merge(merged, info);
            }
        }
        return merged;
    }

    /**
     * 合并两条限制登记项：先到为主，后续实现补空；数值表按键合并，清单取并集。
     *
     * @param target 已合并结果，可为 null
     * @param src    当前实现的命中项
     * @return 合并后的登记项（新对象，不共享实现的内部实例）
     */
    private static ProviderLimitInfo merge(ProviderLimitInfo target, ProviderLimitInfo src) {
        if (target == null) {
            ProviderLimitInfo copy = new ProviderLimitInfo();
            copy.setId(src.getId());
            copy.setName(src.getName());
            copy.setDoc(src.getDoc());
            copy.setVerified(src.isVerified());
            copy.setLimits(copyOf(src.getLimits()));
            copy.setNotes(src.getNotes());
            copy.setAliases(union(src.getAliases(), null));
            copy.setHosts(union(src.getHosts(), null));
            return copy;
        }
        if (isEmpty(target.getId())) {
            target.setId(src.getId());
        }
        if (isEmpty(target.getName())) {
            target.setName(src.getName());
        }
        if (isEmpty(target.getDoc())) {
            target.setDoc(src.getDoc());
        }
        if (!target.isVerified() && src.isVerified()) {
            target.setVerified(true);
        }
        target.setLimits(mergeLimits(target.getLimits(), src.getLimits()));
        if (isEmpty(target.getNotes())) {
            target.setNotes(src.getNotes());
        }
        target.setAliases(union(target.getAliases(), src.getAliases()));
        target.setHosts(union(target.getHosts(), src.getHosts()));
        return target;
    }

    /**
     * 数值表合并：同一键以先到为准，后续实现只补缺。
     *
     * @param target 已合并的数值表
     * @param src    新增数值表
     * @return 合并后的表；两侧都空时返回 null（保留「未登记」语义）
     */
    private static Map<String, Object> mergeLimits(Map<String, Object> target, Map<String, Object> src) {
        if (src == null || src.isEmpty()) {
            return target;
        }
        if (target == null || target.isEmpty()) {
            return copyOf(src);
        }
        Map<String, Object> merged = new LinkedHashMap<>(target);
        for (Map.Entry<String, Object> entry : src.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    /**
     * 复制一份数值表（避免把实现的内部实例交给调用方修改）。
     *
     * @param source 源表
     * @return 新表；源为空时返回 null
     */
    private static Map<String, Object> copyOf(Map<String, Object> source) {
        return source == null || source.isEmpty() ? null : new LinkedHashMap<>(source);
    }

    /**
     * 清单取并集（去重、保序）。
     *
     * @param left  已有清单，可为 null
     * @param right 新增清单，可为 null
     * @return 并集；两侧都空时返回 null（保留「未登记」语义）
     */
    private static List<String> union(List<String> left, List<String> right) {
        boolean leftEmpty = left == null || left.isEmpty();
        boolean rightEmpty = right == null || right.isEmpty();
        if (leftEmpty && rightEmpty) {
            return null;
        }
        Set<String> merged = new LinkedHashSet<>(Math.max(4,
                (leftEmpty ? 0 : left.size()) + (rightEmpty ? 0 : right.size())));
        if (!leftEmpty) {
            merged.addAll(left);
        }
        if (!rightEmpty) {
            merged.addAll(right);
        }
        return new ArrayList<>(merged);
    }

    /**
     * 官方文档入口（只有文档、没有数值时也能拿到）。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 文档地址；未收录返回 null
     */
    public static String docUrl(String platform, String baseUrl) {
        ProviderLimitInfo info = find(platform, baseUrl);
        return info == null ? null : info.getDoc();
    }

    /**
     * 命中的厂商键。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 厂商键；未收录返回 null
     */
    public static String docSource(String platform, String baseUrl) {
        ProviderLimitInfo info = find(platform, baseUrl);
        return info == null ? null : info.getId();
    }

    /**
     * 全部登记项（各实现合计）。
     *
     * @return 登记项
     */
    public static List<ProviderLimitInfo> entries() {
        List<ProviderLimitInfo> all = new ArrayList<>(256);
        for (ProviderLimitProvider provider : providers()) {
            try {
                all.addAll(provider.entries());
            } catch (Exception e) {
                log.debug("[provider-limit] 实现[{}]登记项读取失败: {}", provider.name(), e.getMessage());
            }
        }
        return all;
    }

    /**
     * 登记条目数（各实现自报，取合计）。
     *
     * @return 条目数
     */
    public static int size() {
        int total = 0;
        for (ProviderLimitProvider provider : providers()) {
            total += provider.size();
        }
        return total;
    }

    /**
     * 实现标识清单。
     *
     * @return 标识
     */
    public static List<String> kinds() {
        List<String> kinds = new ArrayList<>(4);
        for (ProviderLimitProvider provider : providers()) {
            kinds.add(provider.name());
        }
        return kinds;
    }

    /**
     * 空串 / null 判定。
     *
     * @param value 文本
     * @return 空返回 true
     */
    private static boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }
}
