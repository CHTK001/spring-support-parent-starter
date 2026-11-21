package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.account.channel.service.AccountChannelModelService;
import com.chua.starter.account.channel.store.SysAccountChannelModel;
import com.chua.starter.ai.support.generate.KeylessGenerateCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 *   <li>本地 ONNX 已就绪模型（face/ocr 等本地管线的裸模型名，经
 *       {@link AiModelCatalogService#listLocalModelDetails()} 只读探测——注册名还在
 *       但权重文件已丢失的模型按不存在处理）与 Embedding SPI 提供商标识
 *       （{@link EmbeddingClient}，rag 向量模型的取值域）；</li>
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
     * 本地模型清单门面：提供「注册名 + 权重文件是否就绪」的只读探测结果。
     *
     * <p>存在性校验不能只看注册名：权重文件被清理或模型根/外部目录未登记后，
     * 请求侧实际解析不到文件，必须按「文件可解析」口径过滤，否则配置中心会把
     * 一个实际不可用的模型一直当成已配置（界面开放、门禁放行、调用必败）。</p>
     */
    private final AiModelCatalogService aiModelCatalogService;

    /**
     * 一次校验批次的目录快照。
     *
     * <p>merged 接口的一个响应里有几十个模型字段，共用一份快照，
     * 避免逐字段重复拉目录。</p>
     *
     * @param modelNames      渠道模型名集合（enabled=1）
     * @param localModels     本地已就绪（权重文件可解析）模型 id + Embedding SPI 提供商标识集合
     * @param localRegistered 本地注册表登记的全部模型 id（无论权重是否就绪）
     * @param keylessModels   免登录出图渠道内置模型 id 集合
     */
    public record Catalog(Set<String> modelNames, Set<String> localModels, Set<String> localRegistered,
                          Set<String> keylessModels) {

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
        Set<String> localRegistered = new HashSet<>();
        try {
            // 只把「权重文件确实可解析」的本地模型算进可用目录：注册名还在但文件已丢失的
            // 模型在请求侧实际不可用（路径解析到推理时才发生、失败也在调用侧），
            // 不能放任配置中心把它当成有效配置。判定与模型选择器同源：
            // listLocalModelDetails 只读探测模型根/外部目录/classpath，不触发联网下载。
            // 同时保留「注册名全集」：本地管线的槽位以文件可解析为唯一事实，
            // 不被渠道目录里的同名登记行救活。
            for (Map<String, Object> detail : aiModelCatalogService.listLocalModelDetails()) {
                Object id = detail.get("modelId");
                if (id == null || String.valueOf(id).isBlank()) {
                    continue;
                }
                localRegistered.add(String.valueOf(id));
                if (Boolean.TRUE.equals(detail.get("available"))) {
                    local.add(String.valueOf(id));
                } else {
                    log.debug("[ModelCatalog] 本地模型文件缺失，按不存在处理: {}", id);
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
        return new Catalog(names, local, localRegistered, keyless);
    }

    /**
     * 判断配置值当前是否仍指向一个可用模型。
     *
     * <p>值形态两种：{@code providerId:model} 复合键（LLM/嵌入等）或裸模型名
     * （face/ocr 等本地管线）。匹配按<b>模型名</b>宽松口径：同名模型跨渠道存在即算存在
     * （路由解析本来就按模型名落渠道），不因配置里记录的渠道被删而误判——
     * 模型还在其它渠道供给就仍然可用。</p>
     *
     * <p><b>本地注册模型例外：</b>值命中本地注册表登记的模型 id 时，以
     * 「权重文件可解析」为唯一事实（{@code localModels}）。本地管线的模型名也可能
     * 在渠道目录里留有同名登记行（历史导入/清单同步），但本地推理只认本地文件——
     * 文件丢了，渠道行救不活，不能让配置中心把实际不可用的本地模型当成已配置。</p>
     *
     * @param rawValue 配置值
     * @param catalog  同一批次共享的目录快照
     * @return 存在为 {@code true}；目录整体不可用时也返回 {@code true}（不确定时不误杀）
     */
    public boolean exists(String rawValue, Catalog catalog) {
        // 路径形态的值不是模型名（如 onnx.model_root 的模型根目录）：不做存在性判定，
        // 避免把目录/文件路径误标成「模型已失效」
        if (isPathLike(rawValue)) {
            return true;
        }
        String model = modelNameOf(rawValue);
        if (model == null) {
            return true;
        }
        // 本地注册模型：文件可解析才算存在，不被渠道目录同名行救活
        if (catalog.localRegistered().contains(model)) {
            return catalog.localModels().contains(model);
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
     * 值是否为文件系统路径形态（Windows 盘符或含反斜杠）。
     *
     * <p>模型名不会包含路径分隔符；配置里确实存路径的字段（模型根目录、工作区目录）
     * 不属于「模型选择项」，不应参与存在性标注。</p>
     *
     * @param rawValue 配置值
     * @return 路径形态为 {@code true}
     */
    private static boolean isPathLike(String rawValue) {
        if (rawValue == null) {
            return false;
        }
        String value = rawValue.trim();
        return value.contains("\\") || value.matches("(?i)^[a-z]:.*");
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
