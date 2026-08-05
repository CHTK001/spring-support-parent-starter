package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiModelDTO;

import java.util.List;

/**
 * AI 模型定义服务接口
 *
 * @author CH
 */
public interface AiModelService {

    /**
     * 查询所有模型列表。
     *
     * @return 模型 DTO 列表
     */
    List<AiModelDTO> list();

    /**
     * 新建模型。
     *
     * @param model 模型 DTO
     * @return 创建后的模型 DTO
     */
    AiModelDTO create(AiModelDTO model);

    /**
     * 更新模型。
     *
     * @param modelId 模型主键
     * @param model   模型 DTO
     * @return 更新后的模型 DTO
     */
    AiModelDTO update(Long modelId, AiModelDTO model);

    /**
     * 删除模型（逻辑删除）。
     *
     * @param modelId 模型主键
     */
    void delete(Long modelId);
}