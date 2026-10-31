package com.chua.starter.ai.support.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 底库分组工具类。
 *
 * <p><b>分组的 NULL 语义：</b>数据库分组列建成之前写入的行是 {@code NULL}，
 * 语义上属于默认组 {@code default}。查默认组时必须把 {@code NULL} 也捞进来，
 * 否则历史数据会「凭空消失」；查非默认组时不带 {@code NULL}。</p>
 *
 * <p>所有公开方法的实现都在类内部自包含，不依赖外部配置 ——
 * 调用方只需把「用户传来的字符串」扔进来，拿到「归一化后的字符串/列表」即可。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public final class AiLibraryGroups {

    /**
     * 默认分组名。
     */
    public static final String DEFAULT = "default";

    /**
     * 单个账号最多可创建的分组数（防止无限建组）。
     */
    public static final int MAX_GROUPS = 16;

    private AiLibraryGroups() {
    }

    /**
     * 归一化单个分组名：{@code null/空白} → {@code default}。
     *
     * <p>这是<b>所有入库路径</b>必须走的归一化：写 DB 前统一调用，
     * 确保落库的分组列永远不是 {@code NULL}（除非显式想存 NULL，但本系统不用）。</p>
     *
     * @param group 原始分组名
     * @return 归一化后的分组名（非空、非空白）
     */
    public static String normalize(String group) {
        if (group == null || group.isBlank()) {
            return DEFAULT;
        }
        String g = group.trim();
        return g.isEmpty() ? DEFAULT : g;
    }

    /**
     * 解析逗号分隔的分组字符串为列表（按首次出现去重、保序、空值归一化为 default）。
     *
     * <p>典型用法：前端传 {@code groups=groupA,groupB} → 这里拿到 {@code ["groupA","groupB"]}。
     * 空串/未传 → {@code ["default"]}（只查默认组，**不等于**查全部）。</p>
     *
     * <p>同一次检索不要重复踩同一个分组，超过 {@link #MAX_GROUPS} 按首次出现截断。</p>
     *
     * @param csv 逗号分隔的分组名字符串（可空）
     * @return 分组名列表（至少 1 项）
     */
    public static List<String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of(DEFAULT);
        }
        String[] parts = csv.split(",");
        // 按首次出现去重保序；空串 / 空白段归一化为 default（不是空名）
        Set<String> seen = new LinkedHashSet<>();
        for (String p : parts) {
            String g = normalize(p);
            if (!seen.contains(g) && seen.size() < MAX_GROUPS) {
                seen.add(g);
            }
        }
        // 清理后可能全部是空白段（被归一化为 default），此时只留 default
        return new ArrayList<>(seen.isEmpty() ? Set.of(DEFAULT) : seen);
    }

    /**
     * 解析分组字符串为列表：空/未传 → 空列表（表示<b>不按分组过滤</b>，即查全部）。
     *
     * <p>管理端「全量列表」调用这个：前端不传分组 = 全部；前端传 {@code groupA,groupB} = 只查这两组。
     * 与 {@link #parse(String)} 的区别：{@code parse} 空值默认查默认组，本方法空值默认查全部。</p>
     *
     * @param csv 逗号分隔的分组名字符串（可空）
     * @return 分组名列表；空列表 = 不过滤
     */
    public static List<String> parseOrAll(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        String[] parts = csv.split(",");
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            out.add(normalize(p));
        }
        return out;
    }

    /**
     * 判断某个分组值（可能是 {@code null}）是否属于给定的分组列表。
     *
     * <p>用于查询构建时的 {@code in(列表) OR (IS NULL AND 列表含默认组)} 逻辑的唯一实现，
     * 避免五六个查询各自手写把 NULL 兜底逻辑写错/漏写。</p>
     *
     * @param groupValue 数据库里的分组值（可能为 {@code null}）
     * @param groups     目标分组列表（已归一化）
     * @return 属于返回 {@code true}
     */
    public static boolean matches(String groupValue, List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return true;
        }
        if (groupValue == null) {
            // NULL 归属默认组
            return groups.contains(DEFAULT);
        }
        return groups.contains(groupValue);
    }

    /**
     * 判断分组名是否合法（非空、非空白、长度限制、字符白名单）。
     *
     * <p>前端「新建分组」按钮点击前可先用这个校验；后端入库前也要再校验一次（防御）。</p>
     *
     * @param group 分组名
     * @return 合法返回 {@code true}
     */
    public static boolean valid(String group) {
        if (group == null || group.isBlank()) {
            return false;
        }
        String g = group.trim();
        if (g.isEmpty() || g.length() > 64) {
            return false;
        }
        // 只允许字母、数字、下划线、中划线、中文
        return g.matches("^[A-Za-z0-9_\\-\\u4e00-\\u9fa5]+$");
    }

    /**
     * 清洗前端传来的分组名：去首尾空白、非法字符替换为下划线、长度截断。
     *
     * <p>不抛异常、不返回 null —— 保证调用方拿到的总是「能落库的字符串」。</p>
     *
     * @param group 原始分组名
     * @return 清洗后的分组名（非空、非空白）
     */
    public static String sanitize(String group) {
        if (group == null || group.isBlank()) {
            return DEFAULT;
        }
        String g = group.trim().replaceAll("[^A-Za-z0-9_\\-\\u4e00-\\u9fa5]", "_");
        if (g.length() > 64) {
            g = g.substring(0, 64);
        }
        return g.isEmpty() ? DEFAULT : g;
    }

    /**
     * 供前端显示的分组标签：{@code default} → {@code 默认组}，其余原样。
     *
     * @param group 分组名
     * @return 显示文案
     */
    public static String display(String group) {
        return DEFAULT.equals(group) ? "默认组" : group;
    }
}