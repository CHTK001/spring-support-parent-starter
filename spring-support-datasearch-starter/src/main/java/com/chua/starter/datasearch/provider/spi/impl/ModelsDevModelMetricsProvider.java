package com.chua.starter.datasearch.provider.spi.impl;

import com.chua.starter.datasearch.provider.model.ProviderDescriptor;
import com.chua.starter.datasearch.provider.model.ProviderModelInfo;
import com.chua.starter.datasearch.provider.spi.ProviderModelMetricsProvider;
import com.chua.starter.datasearch.provider.support.ProviderRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * models.dev 指标实现：按模型 id 在随包快照里查上下文、输出上限、输入/输出模态与能力位。
 *
 * <p>与 {@link ModelsDevCatalogProvider} 共用同一份 {@code provider-catalog/models.dev.json.gz}
 * 快照（同一个懒加载器，不重复解析）：本实现只负责「按模型 id 找到条目」，
 * 目录实现负责「按平台 + 地址找到厂商并遍历模型」。</p>
 *
 * <p>同一模型常被多家厂商收录，挑选顺序：入参平台 / 登记表目录键对应的厂商优先，
 * 其次按字段完整度（模态 &gt; 上下文 &gt; 输出上限），最后取输入价较低的一条
 * （网关加价会把价格抬高，低价那条更接近厂商官方价；输入输出全为 0 的
 * 订阅打包报价视为无价，不参与比价）。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
public class ModelsDevModelMetricsProvider implements ProviderModelMetricsProvider {

    /**
     * 数据源标识
     */
    private static final String SOURCE = "modelsdev";

    @Override
    public String name() {
        return SOURCE;
    }

    @Override
    public boolean supportsModel(String model) {
        return ModelsDevCatalogProvider.hasModel(model);
    }

    @Override
    public ProviderModelInfo query(String platform, String model) {
        List<ModelsDevCatalogProvider.ModelEntry> hits = ModelsDevCatalogProvider.findByModelId(model);
        if (hits.isEmpty()) {
            return null;
        }
        List<String> wanted = wantedKeys(platform);
        ModelsDevCatalogProvider.ModelEntry best = null;
        for (ModelsDevCatalogProvider.ModelEntry hit : hits) {
            if (best == null || better(hit, best, wanted)) {
                best = hit;
            }
        }
        return best == null ? null : best.model();
    }

    @Override
    public int modelCount() {
        return ModelsDevCatalogProvider.totalModels();
    }

    @Override
    public List<ProviderModelInfo> listAll() {
        java.util.Set<String> ids = ModelsDevCatalogProvider.modelIds();
        List<ProviderModelInfo> out = new ArrayList<>(ids.size());
        for (String modelId : ids) {
            // 复用单模型查询的挑选口径（平台无关：按字段完整度、同价取低者）
            ProviderModelInfo info = query(null, modelId);
            if (info != null) {
                out.add(info);
            }
        }
        return out;
    }

    /**
     * 入参平台可能对应的目录厂商键（平台自身 + 登记表给出的 models.dev 键）。
     *
     * <p>登记表能把 {@code qwen}、{@code together}、{@code aws-bedrock} 这类前端 id
     * 映射到目录里的 {@code alibaba}、{@code togetherai}、{@code amazon-bedrock}，
     * 否则同一模型只能靠字段完整度碰运气挑到厂商。</p>
     *
     * @param platform 平台 / 渠道标识
     * @return 折叠后的候选厂商键（可为空列表）
     */
    private static List<String> wantedKeys(String platform) {
        List<String> keys = new ArrayList<>(2);
        String direct = fold(platform);
        if (!direct.isEmpty()) {
            keys.add(direct);
        }
        ProviderDescriptor descriptor = ProviderRegistries.find(platform, null);
        String modelsDevId = descriptor == null ? null : descriptor.getModelsDevId();
        if (modelsDevId != null && !modelsDevId.isBlank()) {
            String folded = fold(modelsDevId);
            if (!folded.isEmpty() && !keys.contains(folded)) {
                keys.add(folded);
            }
        }
        return keys;
    }

    /**
     * 判断候选是否优于当前最优。
     *
     * @param candidate 候选条目
     * @param current   当前最优
     * @param wanted    平台对应的目录厂商键
     * @return 候选更优返回 true
     */
    private static boolean better(ModelsDevCatalogProvider.ModelEntry candidate,
                                  ModelsDevCatalogProvider.ModelEntry current,
                                  List<String> wanted) {
        boolean candidateMatch = wanted.contains(fold(candidate.providerId()));
        boolean currentMatch = wanted.contains(fold(current.providerId()));
        if (candidateMatch != currentMatch) {
            return candidateMatch;
        }
        int candidateScore = score(candidate.model());
        int currentScore = score(current.model());
        if (candidateScore != currentScore) {
            return candidateScore > currentScore;
        }
        Double candidatePrice = effectivePrice(candidate.model());
        Double currentPrice = effectivePrice(current.model());
        if (candidatePrice != null && currentPrice != null && !candidatePrice.equals(currentPrice)) {
            return candidatePrice < currentPrice;
        }
        return candidatePrice != null && currentPrice == null;
    }

    /**
     * 参与比价的有效输入价：输入、输出全为 0 的条目（订阅打包、限时免费等非按量报价）
     * 视为「无价」，避免这类 0/0 报价在价格裁决中压过真实报价。
     *
     * @param model 模型信息
     * @return 有效输入价；视为无价时返回 null
     */
    private static Double effectivePrice(ProviderModelInfo model) {
        Double input = model.getPriceInputPerMusd();
        if (input == null) {
            return null;
        }
        Double output = model.getPriceOutputPerMusd();
        if (input == 0d && (output == null || output == 0d)) {
            return null;
        }
        return input;
    }

    /**
     * 字段完整度打分：模态 &gt; 上下文 &gt; 输出上限。
     *
     * @param model 模型信息
     * @return 分值
     */
    private static int score(ProviderModelInfo model) {
        int score = 0;
        if (model.getInputModalities() != null && !model.getInputModalities().isEmpty()) {
            score += 4;
        }
        if (model.getContextWindow() != null) {
            score += 2;
        }
        if (model.getMaxOutputTokens() != null) {
            score += 1;
        }
        return score;
    }

    /**
     * 折叠厂商标识：小写、去分隔符，便于 {@code opencode-go} 与 {@code opencode} 之外的
     * 大小写 / 连字符差异对齐。
     *
     * @param value 厂商标识
     * @return 折叠值；空值返回空串
     */
    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                builder.append(ch);
            } else if (ch >= 'A' && ch <= 'Z') {
                builder.append((char) (ch + ('a' - 'A')));
            }
        }
        return builder.toString();
    }
}
