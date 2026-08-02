package com.chua.starter.ai.support.service.settings;

import com.chua.starter.ai.support.model.AiAppKeyDTO;

import java.util.List;

/**
 * AppKey 服务接口
 *
 * @author CH
 * @since 2026-08-03
 */
public interface AiAppKeyService {

    /**
     * 查询所有 AppKey。
     *
     * @return AppKey DTO 列表
     */
    List<AiAppKeyDTO> list();

    /**
     * 新建 AppKey。
     *
     * @param appKey AppKey DTO
     * @return 创建后的 AppKey DTO
     */
    AiAppKeyDTO create(AiAppKeyDTO appKey);

    /**
     * 更新 AppKey。
     *
     * @param appKeyId AppKey 主键
     * @param appKey   AppKey DTO
     * @return 更新后的 AppKey DTO
     */
    AiAppKeyDTO update(Long appKeyId, AiAppKeyDTO appKey);

    /**
     * 删除 AppKey（逻辑删除）。
     *
     * @param appKeyId AppKey 主键
     */
    void delete(Long appKeyId);

    /**
     * 切换 AppKey 启用状态。
     *
     * @param appKeyId AppKey 主键
     * @param enabled  是否启用
     * @return 更新后的 AppKey DTO
     */
    AiAppKeyDTO toggle(Long appKeyId, Boolean enabled);
}
