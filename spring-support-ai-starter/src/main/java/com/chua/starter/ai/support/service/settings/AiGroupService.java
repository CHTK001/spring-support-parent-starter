package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiGroupDTO;

import java.util.List;

/**
 * 模型分组服务接口
 *
 * <p>{@code appKeyIds} 的读写口径见实现类：<b>归属以 {@code sys_ai_app_key.sys_ai_app_key_group_id}
 * 为唯一真源</b>，本接口上的 {@code appKeyIds} 是同一份关系的投影 —— 读时按真源算，
 * 写时（{@code null} = 不改，空数组 = 解绑全部，列表 = 最终名单）落到真源。</p>
 *
 * @since 2026-08-03
 * @author CH
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
