package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.starter.account.channel.service.AccountChannelModelService;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.ai.support.generate.KeylessGenerateCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 模型目录存在性校验。
 *
 * <p>设置页的模型字段保存的是「配置时刻」的模型标识，渠道模型随后可能被删除、
 * 停用或改名，而配置值并不会跟着消失——界面与门禁若只看「值非空」，
 * 就会把一个已经不存在的模型当成有效配置。本类只回答一个问题：
 * 这个值现在还指向一个可用模型吗？</p>
 *
 * <p>目录来源与路由解析同口径，共三类：</p>
 * <ul>
 *   <li>渠道模型目录（account 模块供给口径，{@code enabled=1}，走缓存）；</li>
 *   <li>本地 ONNX 注册模型（{@link ModelRegistry}，face/ocr 等本地管线的裸模型名）
 *       与 Embedding SPI 提供商标识（{@link EmbeddingClient}，rag 向量模型的取值域）；</li>
 *   <li>免登录出图渠道内置模型（{@link KeylessGenerateCatalog}）。</li>
 * </ul>
 *
 * <p><b>时效口径：请求时校验，不做定时任务。</b>模型目录读取走 account 的清单缓存，
 * 模型增删改停都会广播 {@code ChannelCatalogChangedEvent} 即时失效缓存，请求时现查
 * 拿到的就是最新目录；merged 接口只在打开设置页时调用，实时校验的开销可以忽略。
 * 定时任务反而是常驻负担，且窗口期内照样存在脏状态，解决不了时效问题。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelCatalogChecker {

    /**
     * 渠道模型目录（account 模块查询契约，走缓存）
     */
    private final AccountChannelModelService channelModelService;

    /**
     * 一次校验批次的目录快照。
     *
     * <p>merged 接口的一个响应里有几十个模型字段，共用一份快照，
     * 避免逐字段重复拉目录。</p>
     *
     * @param modelNames    渠道模型名集合（enabled=1）
     * @param localModels   本地 ONNX 注册模型 id + Embedding SPI 提供商标识集合
     * @param keylessModels 免登录出图渠道内置模型 id 集合
     */
    public record Catalog(Set<String> modelNames, Set<String> localModels, Set<String> keylessModels) {

        /**
         * 目录是否整体不可用（三个来源都为空，通常是查询故障）。
         *
         * @return 不可用为 {@code true}
         */
        boolean unusable() {
            return modelNames.isEmpty() && localModels.isEmpty() && keylessModels.isEmpty();
        }
    }

    /**
     * 采集当前目录快照。
     *
     * @return 目录快照；任一来源读取失败时该来源记为空集，不阻断其余来源
     */
    public Catalog snapshot() {
        Set<String> names = new HashSet<>();
        try {
            List<SysAccountChannelModel> rows = channelModelService.listAllEnabled();
            for (SysAccountChannelModel row : rows) {
                if (row != null && row.getModel() != null && !row.getModel().isBlank()) {
                    names.add(row.getModel());
                }
            }
        } catch (Exception e) {
            log.warn("[ModelCatalog] 渠道模型目录读取失败，本轮按空目录处理: {}", e.getMessage());
        }
        Set<String> local = new HashSet<>();
        try {
            ModelRegistry.discoverAll();
            Collection<ModelRegistry.Entry> entries = ModelRegistry.getAll();
            for (ModelRegistry.Entry entry : entries) {
                if (entry != null && entry.modelId() != null && !entry.modelId().isBlank()) {
                    local.add(entry.modelId());
                }
            }
        } catch (Throwable e) {
            log.debug("[ModelCatalog] 本地模型目录读取失败，本轮按空目录处理: {}", e.getMessage());
        }
        // Embedding SPI 提供商标识（openai/siliconflow/sensetime/内存…）：
        // rag_embedding_model_name 的合法取值含 SPI 提供商名，不只是渠道/本地模型
        try {
            ServiceProvider.of(EmbeddingClient.class).getExtensions().forEach(name -> {
                if (name != null && !name.isBlank()) {
                    local.add(name.toLowerCase());
                }
            });
        } catch (Throwable e) {
            log.debug("[ModelCatalog] Embedding SPI 目录读取失败，本轮按空目录处理: {}", e.getMessage());
        }
        Set<String> keyless = new HashSet<>();
        try {
            for (KeylessGenerateCatalog.ChannelModel row : KeylessGenerateCatalog.imageModels()) {
                if (row != null && row.model() != null && !row.model().isBlank()) {
                    keyless.add(row.model());
                }
            }
        } catch (Throwable e) {
            log.debug("[ModelCatalog] 免登录模型目录读取失败，本轮按空目录处理: {}", e.getMessage());
        }
        return new Catalog(names, local, keyless);
    }

    /**
     * 判断配置值当前是否仍指向一个可用模型。
     *
     * <p>值形态两种：{@code providerId:model} 复合键（LLM/嵌入等）或裸模型名
     * （face/ocr 等本地管线）。匹配按<b>模型名</b>宽松口径：同名模型跨渠道存在即算存在
     * （路由解析本来就按模型名落渠道），不因配置里记录的渠道被删而误判——
     * 模型还在其它渠道供给就仍然可用。</p>
     *
     * @param rawValue 配置值
     * @param catalog  同一批次共享的目录快照
     * @return 存在为 {@code true}；目录整体不可用时也返回 {@code true}（不确定时不误杀）
     */
    public boolean exists(String rawValue, Catalog catalog) {
        String model = modelNameOf(rawValue);
        if (model == null) {
            return true;
        }
        if (catalog.modelNames().contains(model)
                || catalog.localModels().contains(model)
                || catalog.keylessModels().contains(model)) {
            return true;
        }
        // 三个来源都为空视为目录不可用（查询故障），按存在处理，避免把好配置全部标坏
        return catalog.unusable();
    }

    /**
     * 从配置值里解析模型名。
     *
     * @param rawValue 配置值
     * @return 模型名；空值返回 {@code null}（调用方按「无需校验」处理）
     */
    public static String modelNameOf(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return null;
        }
        // 复合键 providerId:model 取冒号后的模型名；裸模型名原样返回
        int idx = value.indexOf(':');
        String model = idx > 0 ? value.substring(idx + 1) : value;
        model = model.trim();
        return model.isEmpty() ? null : model;
    }
}
