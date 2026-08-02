package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiGroupDTO;

import java.util.List;

/**
 * 模型分组服务接口
 *
 * @author CH
 * @since 2026-08-03
 */
public interface AiGroupService {

    /**
     * 查询所有分组。
     *
     * @return 分组 DTO 列表
     */
    List<AiGroupDTO> list();

    /**
     * 新建分组。
     *
     * @param group 分组 DTO
     * @return 创建后的分组 DTO
     */
    AiGroupDTO create(AiGroupDTO group);

    /**
     * 更新分组。
     *
     * @param groupId 分组主键
     * @param group   分组 DTO
     * @return 更新后的分组 DTO
     */
    AiGroupDTO update(Long groupId, AiGroupDTO group);

    /**
     * 删除分组（逻辑删除）。
     *
     * @param groupId 分组主键
     */
    void delete(Long groupId);
}
