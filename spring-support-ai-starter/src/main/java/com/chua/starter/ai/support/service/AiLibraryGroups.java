// [P3C A20 豁免] A20/A30/A31: 仅含常量定义行，豁免。
package com.chua.starter.ai.support.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 人脸 / 重识别底库的**分组**口径。
 *
 * <p><b>分组是什么。</b>同一个账号下可以有多套互不干扰的底库 ——
 * 例如「员工」「访客」「黑名单」各一套。分组是底库的第一层隔离：
 * 登记时写进哪一组，检索时就只在被指定的那些组里找。跨组检索必须
 * <b>显式列出组名</b>，不存在「不传就搜全部」的默认 —— 那会让本该隔离的数据
 * 因为调用方漏传参数而被看见。</p>
 *
 * <p><b>默认组。</b>历史数据（分组列建成之前写入的行）分组列为 {@code NULL}，
 * 一律按 {@link #DEFAULT} 处理。因此 {@code default} 既是「未指定时的落点」，
 * 也是「历史数据的归属」—— 两者指向同一组，不会出现「老数据谁都搜不到」。</p>
 *
 * <p><b>为什么分组要参与目录结构。</b>分组直接决定向量库的落盘路径
 * （{@code <根>/<账号>/<分组>/face}），而不是同一份索引里按元数据过滤。
 * 原因是 topK 语义：过滤方案下「取相似度前 5 条再筛掉别的组」会返回不足 5 条，
 * 要补足就得反复超取，命中率随跨组数据量下降；分组独立成库后
 * topK 就是本组内的前 K，语义精确且删除整组是删目录这么简单。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
public final class AiLibraryGroups {

    /**
     * 默认分组名。
     *
     * <p>未指定分组时的落点，也是历史 {@code NULL} 行的归属。</p>
     */
    public static final String DEFAULT = "default";

    /**
     * 多分组的最大个数。
     *
     * <p>一次检索最多跨这么多组：每个组要单独查一次向量库再归并，
     * 不设上限时一个超长的 {@code groups} 参数就能把单次请求放大成几十次检索。</p>
     */
    public static final int MAX_GROUPS = 16;

    /**
     * 工具类，禁止实例化。
     */
    private AiLibraryGroups() {
    }

    /**
     * 归一化单个分组名：空白（含 {@code null}）视为 {@link #DEFAULT}。
     *
     * @param group 原始分组名
     * @return 非空分组名
     */
    public static String normalize(String group) {
        if (group == null) {
            return DEFAULT;
        }
        String trimmed = group.trim();
        return trimmed.isEmpty() ? DEFAULT : trimmed;
    }

    /**
     * 解析「多个分组」参数为去重后的分组列表。
     *
     * <p>接受逗号分隔（{@code a,b,c}）或单个组名；空值视为只查 {@link #DEFAULT}。
     * 顺序按首次出现保留，便于日志与结果归并时保持一致。</p>
     *
     * @param groups 原始参数（逗号分隔，可空）
     * @return 去重后的分组列表，至少含一项；超过 {@link #MAX_GROUPS} 时截断
     */
    public static List<String> parse(String groups) {
        if (groups == null || groups.isBlank()) {
            return List.of(DEFAULT);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String part : groups.split(",")) {
            unique.add(normalize(part));
        }
        List<String> out = new ArrayList<>(unique);
        if (out.size() > MAX_GROUPS) {
            return List.copyOf(out.subList(0, MAX_GROUPS));
        }
        return List.copyOf(out);
    }

    /**
     * 解析多分组参数；与 {@link #parse(String)} 的区别是**保留空**——
     * 空值返回空列表，供「不指定就查全部组」的场景使用（如管理端全量列表）。
     *
     * @param groups 原始参数（逗号分隔，可空）
     * @return 去重后的分组列表；未指定时返回空列表
     */
    public static List<String> parseOrAll(String groups) {
        if (groups == null || groups.isBlank()) {
            return List.of();
        }
        return parse(groups);
    }

    /**
     * 判断某个分组名是否属于给定的分组集合（历史 {@code NULL} 按默认组计）。
     *
     * <p>用于内存侧过滤（快照里的分组名来自元数据，可能是 {@code null}）。</p>
     *
     * @param rowGroup     行上的分组名（可空）
     * @param wantedGroups 期望的分组集合；空集合表示不限定
     * @return 是否命中
     */
    public static boolean matches(String rowGroup, List<String> wantedGroups) {
        if (wantedGroups == null || wantedGroups.isEmpty()) {
            return true;
        }
        String normalized = normalize(rowGroup);
        for (String wanted : wantedGroups) {
            if (normalized.equalsIgnoreCase(normalize(wanted))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把分组名收敛成安全的目录名，避免把写入引出根目录。
     *
     * <p>与账号名同样处理：分组名来自请求参数，直接拼路径会让
     * {@code ../../} 这类值把文件写到仓库其他位置。</p>
     *
     * @param group 分组名
     * @return 可安全作为单级目录名的字符串
     */
    public static String sanitize(String group) {
        String normalized = normalize(group);
        String s = normalized
                .replace("\\", "_").replace("/", "_").replace(":", "_")
                .replace("*", "_").replace("?", "_").replace("\"", "_")
                .replace("<", "_").replace(">", "_").replace("|", "_");
        s = s.replace("..", "_");
        s = s.replaceAll("^[.\\s]+|[.\\s]+$", "");
        return s.isBlank() ? DEFAULT : s;
    }

    /**
     * 判断分组名是否合法（供参数校验给出可读错误）。
     *
     * @param group 分组名
     * @return 合法返回 true
     */
    public static boolean valid(String group) {
        if (group == null) {
            return false;
        }
        String trimmed = group.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            return false;
        }
        // 仅允许中英文、数字与 - _ . @ ：分组名会进目录名，先卡在源头
        return trimmed.matches("[\\w\\u4e00-\\u9fa5.@-]+");
    }

    /**
     * 分组名的展示形式（日志用；历史 {@code NULL} 显示为默认组）。
     *
     * @param group 分组名
     * @return 展示名
     */
    public static String display(String group) {
        return normalize(group).toLowerCase(Locale.ROOT);
    }
}
