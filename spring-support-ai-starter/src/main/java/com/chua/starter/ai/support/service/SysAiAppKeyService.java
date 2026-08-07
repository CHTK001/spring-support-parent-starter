package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiAppKey;

import java.util.List;
import java.util.Optional;

/**
 * AI AppKey 服务接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface SysAiAppKeyService {

    /**
     * 查询全部 AppKey。
     *
     * @return AppKey 列表
     */
    List<SysAiAppKey> list();

    /**
     * 新增 AppKey。
     *
     * @param appKey AppKey 实体
     * @return 保存后的 AppKey
     */
    SysAiAppKey save(SysAiAppKey appKey);

    /**
     * 更新 AppKey。
     *
     * @param appKey AppKey 实体
     * @return 是否成功
     */
    boolean update(SysAiAppKey appKey);

    /**
     * 按 ID 删除 AppKey。
     *
     * @param id AppKey ID
     * @return 是否成功
     */
    boolean deleteById(Integer id);

    /**
     * 按 AppKey 查询。
     *
     * @param appKey AppKey
     * @return AppKey 实体（可选）
     */
    default Optional<SysAiAppKey> findByAppKey(String appKey) {
        return Optional.ofNullable(getByAppKey(appKey));
    }

    SysAiAppKey getByAppKey(String appKey);

    Integer getGroupIdByAppKey(String appKey);

    boolean verify(String appKey, String secret, String ip);

    void updateUseInfoByAppKey(String appKey);
}
