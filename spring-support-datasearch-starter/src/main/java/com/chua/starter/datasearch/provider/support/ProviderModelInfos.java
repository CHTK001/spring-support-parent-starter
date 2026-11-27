package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderModelInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 模型信息的合并口径：多个 SPI 实现各给一半字段时，合成一份完整的模型信息。
 *
 * <p>为什么需要独立一份：模型数据天然分散——公开目录有模态与上下限、厂商控制台有真实
 * 牌价、协议能力位有流式/异步。如果出口类写成「第一个命中算数」，先命中的那个实现
 * 没给的字段就永久为空，页面表现为「一边有、一边空」。合并口径统一在这里，目录出口
 * （{@link ProviderModelCatalogs}）与指标出口（{@link ProviderModelMetrics}）共用，
 * 避免同一个能力落到两份实现（两套合并规则迟早会漂移）。</p>
 *
 * <p>合并原则：</p>
 * <ul>
 *   <li><b>先到为主</b>：文本、价格、上下限、布尔位都取第一个非空值，后面的源只补空。</li>
 *   <li><b>模态取并集</b>：输入 / 输出模态是多值字段，并集不会把「支持图片」丢掉。</li>
 *   <li><b>空不等于零</b>：{@code null} 与空串都算「未声明」，仍可被后续源补齐；
 *       {@code false} / {@code 0} 是明确取值，不会被覆盖。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.58
 */
public final class ProviderModelInfos {

    /**
     * 工具类，禁止实例化。
     */
    private ProviderModelInfos() {
    }

    /**
     * 合并两条模型信息：目标缺失的字段用源补齐（空列表也视为缺失，模态取并集）。
     *
     * <p>价格只从第一个给出非空值的源取，不在源之间混算。返回的对象可以直接修改，
     * 不与实现的内部实例共享（首次调用时新建）。</p>
     *
     * @param target 已合并结果，可为 {@code null}
     * @param src    当前实现的命中记录，可为 {@code null}
     * @return 合并后的结果；两侧都为 null 时返回 {@code null}
     */
    public static ProviderModelInfo merge(ProviderModelInfo target, ProviderModelInfo src) {
        if (src == null) {
            return target;
        }
        ProviderModelInfo out = target == null ? new ProviderModelInfo() : target;
        if (isEmpty(out.getId())) {
            out.setId(src.getId());
        }
        if (isEmpty(out.getName())) {
            out.setName(src.getName());
        }
        if (isEmpty(out.getDescription())) {
            out.setDescription(src.getDescription());
        }
        if (out.getPriceInputPerMusd() == null) {
            out.setPriceInputPerMusd(src.getPriceInputPerMusd());
        }
        if (out.getPriceOutputPerMusd() == null) {
            out.setPriceOutputPerMusd(src.getPriceOutputPerMusd());
        }
        if (out.getPriceCacheHitPerMusd() == null) {
            out.setPriceCacheHitPerMusd(src.getPriceCacheHitPerMusd());
        }
        if (out.getPriceCacheWritePerMusd() == null) {
            out.setPriceCacheWritePerMusd(src.getPriceCacheWritePerMusd());
        }
        if (out.getContextWindow() == null) {
            out.setContextWindow(src.getContextWindow());
        }
        if (out.getMaxOutputTokens() == null) {
            out.setMaxOutputTokens(src.getMaxOutputTokens());
        }
        out.setInputModalities(union(out.getInputModalities(), src.getInputModalities()));
        out.setOutputModalities(union(out.getOutputModalities(), src.getOutputModalities()));
        out.setAttachment(firstNonNull(out.getAttachment(), src.getAttachment()));
        out.setReasoning(firstNonNull(out.getReasoning(), src.getReasoning()));
        out.setToolCall(firstNonNull(out.getToolCall(), src.getToolCall()));
        out.setStructuredOutput(firstNonNull(out.getStructuredOutput(), src.getStructuredOutput()));
        out.setTemperature(firstNonNull(out.getTemperature(), src.getTemperature()));
        out.setOpenWeights(firstNonNull(out.getOpenWeights(), src.getOpenWeights()));
        out.setStreaming(firstNonNull(out.getStreaming(), src.getStreaming()));
        if (isEmpty(out.getStreamingSource())) {
            out.setStreamingSource(src.getStreamingSource());
        }
        out.setAsyncSupported(firstNonNull(out.getAsyncSupported(), src.getAsyncSupported()));
        if (isEmpty(out.getAsyncSource())) {
            out.setAsyncSource(src.getAsyncSource());
        }
        if (isEmpty(out.getKnowledge())) {
            out.setKnowledge(src.getKnowledge());
        }
        if (isEmpty(out.getReleaseDate())) {
            out.setReleaseDate(src.getReleaseDate());
        }
        if (isEmpty(out.getLastUpdated())) {
            out.setLastUpdated(src.getLastUpdated());
        }
        return out;
    }

    /**
     * 按模型 id 合并两个模型清单：同 id 的合成一条（前者为主、后者补空），其余追加。
     *
     * <p>id 比较忽略大小写与首尾空白；没有 id 的条目按「不可判定」处理，直接追加，
     * 不做猜测性匹配。</p>
     *
     * @param primary 主清单，可为 {@code null}
     * @param extra   补充清单，可为 {@code null}
     * @return 合并后的新清单（不改动入参里的元素）
     */
    public static List<ProviderModelInfo> mergeModels(List<ProviderModelInfo> primary,
                                                     List<ProviderModelInfo> extra) {
        List<ProviderModelInfo> out = new ArrayList<>(
                (primary == null ? 0 : primary.size()) + (extra == null ? 0 : extra.size()));
        Map<String, Integer> index = new LinkedHashMap<>(Math.max(16, out.size()));
        copyInto(primary, out, index);
        if (extra != null) {
            for (ProviderModelInfo model : extra) {
                if (model == null) {
                    continue;
                }
                String key = keyOf(model.getId());
                Integer at = key == null ? null : index.get(key);
                if (at == null) {
                    if (key != null) {
                        index.put(key, out.size());
                    }
                    out.add(copyOf(model));
                    continue;
                }
                out.set(at, merge(out.get(at), model));
            }
        }
        return out;
    }

    /**
     * 把源清单整体拷进结果并建索引。
     *
     * @param source 源清单，可为 null
     * @param out    结果清单
     * @param index  模型 id → 下标
     */
    private static void copyInto(List<ProviderModelInfo> source, List<ProviderModelInfo> out,
                                 Map<String, Integer> index) {
        if (source == null) {
            return;
        }
        for (ProviderModelInfo model : source) {
            if (model == null) {
                continue;
            }
            String key = keyOf(model.getId());
            if (key != null) {
                Integer at = index.get(key);
                if (at != null) {
                    out.set(at, merge(out.get(at), model));
                    continue;
                }
                index.put(key, out.size());
            }
            out.add(copyOf(model));
        }
    }

    /**
     * 复制一条模型信息（只在空对象上合并，避免把实现的实例带出去被调用方改掉）。
     *
     * @param model 源模型
     * @return 新对象
     */
    private static ProviderModelInfo copyOf(ProviderModelInfo model) {
        return merge(null, model);
    }

    /**
     * 索引键（小写去空白）。
     *
     * @param id 模型 id
     * @return 键；空 id 返回 {@code null}
     */
    private static String keyOf(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 取并集（保持原顺序并去重）。
     *
     * @param left  已合并列表，可为 null
     * @param right 新列表，可为 null
     * @return 并集；两侧都空时返回 null（保留「未声明」语义，不写成空列表）
     */
    private static List<String> union(List<String> left, List<String> right) {
        if (CollectionUtils.isEmpty(left)) {
            return CollectionUtils.isEmpty(right) ? left : new ArrayList<>(right);
        }
        if (CollectionUtils.isEmpty(right)) {
            return left;
        }
        List<String> merged = new ArrayList<>(left.size() + right.size());
        merged.addAll(left);
        for (String item : right) {
            if (item != null && !merged.contains(item)) {
                merged.add(item);
            }
        }
        return merged;
    }

    /**
     * 目标为空时取源值。
     *
     * @param target 已有值
     * @param value  候选值
     * @return 目标或候选
     */
    private static Boolean firstNonNull(Boolean target, Boolean value) {
        return target != null ? target : value;
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
