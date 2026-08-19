package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiProviderDTO;

import java.util.List;

/**
 * 模型供应商服务接口
 *
 * @since 2026-08-03
 * @author CH
 */
public interface AiProviderService {

    /**
     * 查询所有启用状态的供应商。
     *
     * @return 供应商 DTO 列表
     */
    List<AiProviderDTO> list();

    /**
     * 新建供应商。
     *
     * @param provider 供应商 DTO
     * @return 创建后的供应商 DTO
     */
    AiProviderDTO create(AiProviderDTO provider);

    /**
     * 更新供应商。
     *
     * @param providerId 供应商主键
     * @param provider   供应商 DTO
     * @return 更新后的供应商 DTO
     */
    AiProviderDTO update(Long providerId, AiProviderDTO provider);

    /**
     * 删除供应商（逻辑删除）。
     *
     * @param providerId 供应商主键
     */
    void delete(Long providerId);

    /**
     * 切换供应商启用状态。
     *
     * @param providerId 供应商主键
     * @param enabled    是否启用
     * @return 更新后的供应商 DTO
     */
    AiProviderDTO toggle(Long providerId, Boolean enabled);

    /**
     * 启用供应商为 OpenAI 兼容服务（写入 runtime 分组配置并热重建全局 ChatClient）。
     *
     * <p>执行后立即可通过 {@code /v2/v1/chat/completions} 等 OpenAI 兼容端点请求。</p>
     *
     * @param providerId 供应商主键
     * @return 更新后的供应商 DTO
     */
    AiProviderDTO activate(Long providerId);
}
