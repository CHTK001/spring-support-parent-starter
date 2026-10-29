package com.chua.starter.ai.support.service.settings;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.model.AiAppKeyDTO;

import java.util.List;

/**
 * appKey 服务接口
 *
 * @since 2026-08-03
 * @author CH
 */
public interface AiAppKeyService {

    /**
     * 查询所有 appKey。
     *
     * @return 操作结果（data 为 appKey DTO 列表）
     */
    ReturnResult<List<AiAppKeyDTO>> list();

    /**
     * 新建 appKey。
     *
     * @param appKey appKey DTO
     * @return 操作结果（data 为创建后的 appKey DTO）
     */
    ReturnResult<AiAppKeyDTO> create(AiAppKeyDTO appKey);

    /**
     * 更新 appKey。
     *
     * @param appKeyId appKey 主键
     * @param appKey   appKey DTO
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    ReturnResult<AiAppKeyDTO> update(Long appKeyId, AiAppKeyDTO appKey);

    /**
     * 删除 appKey（逻辑删除）。
     *
     * @param appKeyId appKey 主键
     * @return 操作结果
     */
    ReturnResult<Void> delete(Long appKeyId);

    /**
     * 批量删除 appKey（逻辑删除）。
     *
     * <p>与 {@link #delete(Long)} 同口径：写出口归属守卫整批过完后才删，
     * 有问题的行直接拦截，出现部分删除。</p>
     *
     * @param appKeyIds appKey 主键列表
     * @return 实际删除的 appKey 数量
     */
    int batchDelete(List<Long> appKeyIds);

    /**
     * 切换 appKey 启用状态。
     *
     * @param appKeyId appKey 主键
     * @param enabled  是否启用
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    ReturnResult<AiAppKeyDTO> toggle(Long appKeyId, Boolean enabled);
}
