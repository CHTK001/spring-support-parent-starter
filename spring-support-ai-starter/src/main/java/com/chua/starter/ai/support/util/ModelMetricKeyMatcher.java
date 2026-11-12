// [P3C A20 豁免] A20/A30/A31: 仅含常量定义行，豁免。
package com.chua.starter.ai.support.util;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

/**
 * 模型指标键的兜底匹配规则：认「同一个模型的不同写法」，不认「另一个模型」。
 *
 * <p>指标源（Artificial Analysis）的 slug 与本地配置的模型名不总是逐字相同，常见三类差异：</p>
 * <ol>
 *   <li><b>只有版本/日期尾部不同</b>：{@code claude-3-5-sonnet} ↔ {@code claude-3-5-sonnet-20241022}；
 *       {@code muse-wonder} ↔ {@code muse-wonder-1-0}（差异在本地一侧）</li>
 *   <li><b>多出规格词</b>：{@code gpt-5-4} ↔ {@code gpt-5-4-mini}；{@code glm-5-3-flash-free} ↔ {@code glm-5-3}</li>
 *   <li><b>多出推理档位</b>：{@code claude-opus-5-5} ↔ {@code claude-opus-5-5-medium}</li>
 * </ol>
 *
 * <p><b>只有第 1 类算同一模型</b>。第 2 类是另一个模型（价格、速度、智能指数都不同），
 * 第 3 类是同一模型的不同推理档位（价格相同但速度/首字延迟是那一档的数字），
 * 二者都必须拒绝 —— 否则卡片上会出现「gpt-5-4 价格 = mini 的价格」这种凭空捏造的数字。</p>
 *
 * <p><b>为什么要重写：</b>原实现是「双向 {@code contains}，取遍历到的第一个候选」，
 * 两个后果都不可接受：</p>
 * <ol>
 *   <li><b>跨模型串数据</b>：只要精确键没命中，{@code glm-5-3-flash-free} 就会拿到
 *       {@code glm-5-3}（非 flash）的速度，{@code gemini-3-5-flash} 会拿到
 *       {@code gemini-3-5-flash-lite} 的价格；</li>
 *   <li><b>结果不可复现</b>：遍历的是 {@code ConcurrentHashMap}，实际命中的是
 *       <b>hash 桶下标最小的那个</b>。指标源每同步一次模型，表就可能扩容重排，
 *       于是同一个模型两次同步拿到两套数字（已用 JDK 复现：175→195 条时
 *       {@code claude-sonnet-5} 从 {@code -5-5-low} 翻到 {@code -5-5-medium}）。</li>
 * </ol>
 *
 * <p><b>现在的规则：</b>候选必须是在请求键尾部（或头部）多出一个后缀，且后缀的每一段
 * 都是「版本号 / 日期 / 同义别名」；命中多个时取<b>长度差最小</b>的那个，
 * 长度差相同再按字典序取最小。同一输入永远得到同一结果，与容器遍历顺序无关。</p>
 *
 * <p><b>已知取舍：</b>纯数字后缀被视为「同一家族的不同版本/日期快照」，因此
 * {@code glm-5} 会取到 {@code glm-5-3} 的价格。这是「同族相邻版本」而不是「跨规格」，
 * 属于有数据但可能略偏；若要收紧为「只认同日期，不认版本号」，把
 * {@link #MAX_NUMERIC_TOKEN_LENGTH} 调到 4 及以上并去掉短数字即可。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public final class ModelMetricKeyMatcher {

    /**
     * 允许出现在后缀里的同义别名：同一模型在指标源里的不同写法。
     *
     * <p>刻意不含 {@code low/medium/high/xhigh/instant/non-reasoning/reasoning/thinking}，
     * 它们是同一模型的推理档位，速度与首字延迟各档不同，套到基础模型上就是假数字。</p>
     */
    private static final Set<String> ALIAS_TOKENS = Set.of(
            "latest", "preview", "exp", "experimental", "instruct", "chat");

    /**
     * 纯数字后缀片段的最大长度，覆盖 {@code 20241022}、{@code 1017} 这类日期写法。
     */
    private static final int MAX_NUMERIC_TOKEN_LENGTH = 8;

    /**
     * 参与匹配的最短基础键长度：3 个字符及以内（{@code o3}、{@code r1}、{@code gpt}）不做变体匹配，
     * 这类短名极易被大量候选命中；{@code glm-5}、{@code qwen} 这类 4~5 字符的正式型号名要允许。
     */
    private static final int MIN_BASE_KEY_LENGTH = 4;

    /**
     * 工具类不允许实例化。
     */
    private ModelMetricKeyMatcher() {
    }

    /**
     * 判断候选键是否为请求键的「同模型变体」：只在头尾多出可解释的后缀。
     *
     * @param baseKey      请求键（已归一化）
     * @param candidateKey 候选键（已归一化）
     * @return 同模型变体返回 {@code true}；相同键、跨型号、跨档位返回 {@code false}
     */
    public static boolean isVariantKey(String baseKey, String candidateKey) {
        if (baseKey == null || candidateKey == null || baseKey.equals(candidateKey)) {
            return false;
        }
        if (baseKey.length() >= MIN_BASE_KEY_LENGTH && isAliasSuffixOf(baseKey, candidateKey)) {
            return true;
        }
        return candidateKey.length() >= MIN_BASE_KEY_LENGTH && isAliasSuffixOf(candidateKey, baseKey);
    }

    /**
     * 在候选集合里挑出唯一确定的同模型变体。
     *
     * <p>选择规则（与容器遍历顺序无关）：先取与请求键长度差最小的候选，
     * 长度差相同按键字典序取最小。没有合法候选时返回 {@code null}，
     * 由调用方走兜底定价或留空 —— <b>宁可没有数字，也不要别的模型的数字</b>。</p>
     *
     * @param baseKey    请求键（已归一化）
     * @param candidates 候选集合，允许为 {@code null}
     * @param keyOf      从候选对象取键的函数
     * @param <T>        候选类型
     * @return 命中的候选，未命中返回 {@code null}
     */
    public static <T> T selectNearest(String baseKey, Collection<T> candidates,
                                      Function<T, String> keyOf) {
        if (baseKey == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        T best = null;
        String bestKey = null;
        int bestGap = Integer.MAX_VALUE;
        for (T candidate : candidates) {
            String key = keyOf.apply(candidate);
            if (!isVariantKey(baseKey, key)) {
                continue;
            }
            int gap = Math.abs(key.length() - baseKey.length());
            if (bestKey == null || gap < bestGap
                    || (gap == bestGap && key.compareTo(bestKey) < 0)) {
                best = candidate;
                bestKey = key;
                bestGap = gap;
            }
        }
        return best;
    }

    /**
     * 判断 {@code longer} 是否为 {@code base} 加一个可解释后缀。
     *
     * @param base   基础键
     * @param longer 更长的候选键
     * @return 后缀合法返回 {@code true}
     */
    private static boolean isAliasSuffixOf(String base, String longer) {
        String prefix = base + "-";
        if (!longer.startsWith(prefix)) {
            return false;
        }
        return isAliasSuffix(longer.substring(prefix.length()));
    }

    /**
     * 判断后缀（已去掉前导连字符，可能含多段）是否全部由别名/版本/日期组成。
     *
     * @param suffix 后缀
     * @return 全部片段合法返回 {@code true}
     */
    private static boolean isAliasSuffix(String suffix) {
        if (suffix.isEmpty()) {
            return false;
        }
        for (String token : suffix.split("-")) {
            if (!isAliasToken(token)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断单个后缀片段是否可接受：别名词，或纯数字（版本号/日期）。
     *
     * @param token 后缀片段
     * @return 可接受返回 {@code true}
     */
    private static boolean isAliasToken(String token) {
        if (token.isEmpty()) {
            return false;
        }
        if (ALIAS_TOKENS.contains(token)) {
            return true;
        }
        if (token.length() > MAX_NUMERIC_TOKEN_LENGTH) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
