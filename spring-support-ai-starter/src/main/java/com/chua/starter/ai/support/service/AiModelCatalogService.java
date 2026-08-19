package com.chua.starter.ai.support.service;

import java.util.List;
import java.util.Map;

/**
 * AI 统一能力模型清单服务。
 *
 * <p>聚合系统全部能力门面的模型清单，按能力标签分组：
 * <ul>
 *   <li>本地模型：从 {@code ModelRegistry} 按注册时的能力接口归类（detect/feature/classify/enhance/ocr/layout/pose 等）</li>
 *   <li>云端模型：从各能力客户端门面（ChatClient/ImageClient/VideoClient/EmbeddingClient 等）的 {@code listModels()} 聚合</li>
 * </ul>
 * 供前端按能力筛选、模型管理与统一配置使用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface AiModelCatalogService {

    /**
     * 获取按能力分组的所有模型清单。
     *
     * @return 能力标签 → 模型 ID 列表
     */
    Map<String, List<String>> listModelsByCapability();

    /**
     * 获取指定能力的模型清单。
     *
     * @param capability 能力标签（如 detect / feature / chat / text-to-image）
     * @return 模型 ID 列表
     */
    List<String> listModels(String capability);

    /**
     * 获取全部能力标签。
     *
     * @return 能力标签列表
     */
    List<String> listCapabilities();
}
