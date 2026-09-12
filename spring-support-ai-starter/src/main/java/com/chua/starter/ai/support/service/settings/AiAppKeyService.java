package com.chua.starter.ai.support.service.settings;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.model.AiAppKeyDTO;

import java.util.List;

/**
   * app钥匙 服务接口
 *
 * @since 2026-08-03
 * @author CH
 */
public interface AiAppKeyService {

    /**
      * 查询所有 app钥匙。
     *
     * @return 操作结果（data 为 app钥匙 DTO 列表）
     */
    ReturnResult<List<AiAppKeyDTO>> list();

    /**
      * 新建 app钥匙。
     *
     * @param appKey app钥匙 DTO
     * @return 操作结果（data 为创建后的 app钥匙 DTO）
     */
    ReturnResult<AiAppKeyDTO> create(AiAppKeyDTO appKey);

    /**
      * 更新 app钥匙。
     *
     * @param appKeyId app钥匙 主键
     * @param appKey   app钥匙 DTO
     * @return 操作结果（data 为更新后的 app钥匙 DTO）
     */
    ReturnResult<AiAppKeyDTO> update(Long appKeyId, AiAppKeyDTO appKey);

    /**
      * 删除 app钥匙（逻辑删除）。
     *
     * @param appKeyId app钥匙 主键
     * @return 操作结果
     */
    ReturnResult<Void> delete(Long appKeyId);

    /**
      * 切换 app钥匙 启用状态。
     *
     * @param appKeyId app钥匙 主键
     * @param enabled  是否启用
     * @return 操作结果（data 为更新后的 app钥匙 DTO）
     */
    ReturnResult<AiAppKeyDTO> toggle(Long appKeyId, Boolean enabled);
}