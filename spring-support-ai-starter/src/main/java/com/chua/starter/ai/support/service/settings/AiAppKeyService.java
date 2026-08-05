package com.chua.starter.ai.support.service.settings;

import com.chua.common.support.lang.code.ReturnResult;
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
     * @return 操作结果（data 为 AppKey DTO 列表）
     */
    ReturnResult<List<AiAppKeyDTO>> list();

    /**
     * 新建 AppKey。
     *
     * @param appKey AppKey DTO
     * @return 操作结果（data 为创建后的 AppKey DTO）
     */
    ReturnResult<AiAppKeyDTO> create(AiAppKeyDTO appKey);

    /**
     * 更新 AppKey。
     *
     * @param appKeyId AppKey 主键
     * @param appKey   AppKey DTO
     * @return 操作结果（data 为更新后的 AppKey DTO）
     */
    ReturnResult<AiAppKeyDTO> update(Long appKeyId, AiAppKeyDTO appKey);

    /**
     * 删除 AppKey（逻辑删除）。
     *
     * @param appKeyId AppKey 主键
     * @return 操作结果
     */
    ReturnResult<Void> delete(Long appKeyId);

    /**
     * 切换 AppKey 启用状态。
     *
     * @param appKeyId AppKey 主键
     * @param enabled  是否启用
     * @return 操作结果（data 为更新后的 AppKey DTO）
     */
    ReturnResult<AiAppKeyDTO> toggle(Long appKeyId, Boolean enabled);
}