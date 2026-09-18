package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiModelDTO;

import java.util.List;

/**
* AI 模型定义服务接口
*
* @author CH
* @since 4.0.0
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

    /**
    * 测试模型连通性延迟（非本地模型）。
    *
    * <p>发送一个最小请求（max_tokens=1）测量端到端延迟，
    * 结果写入模型记录的 lastLatency / lastTestTime 字段。</p>
    *
    * @param modelId 模型主键
    * @return 测试结果 DTO（含延迟毫秒数和状态）
    */
    AiModelDTO testLatency(Long modelId);
}
