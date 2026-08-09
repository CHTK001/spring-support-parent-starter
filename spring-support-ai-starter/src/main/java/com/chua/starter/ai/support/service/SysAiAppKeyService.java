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

    /**
     * 仅校验 AppKey 是否有效（状态、过期、IP 白名单）。
     * <p>用于 Bearer/Header 单项凭证鉴权场景，AppKey 即完整凭证，不要求额外的 secret。</p>
     *
     * @param appKey AppKey
     * @param ip     客户端 IP（可为空）
     * @return 是否有效
     */
    boolean verifyAppKeyOnly(String appKey, String ip);

    void updateUseInfoByAppKey(String appKey);

    /**
     * 在同一行上补写 OpenAI 兼容鉴权列（app_key / secret / status / group）。
     *
     * <p>面板 AppKey 与兼容鉴权共用同表，本方法只更新兼容列，避免重复行。</p>
     *
     * @param id      面板 AppKey 主键（兼容列按 id 定位）
     * @param key     兼容鉴权密钥
     * @param status  状态：1 启用，0 禁用
     * @param groupId 分组 ID（可空）
     */
    void updateCompatibleColumns(Long id, String key, Integer status, Integer groupId);

    /**
     * 同步过期时间为协议鉴权使用的 epoch 秒列（sys_ai_app_key_expire_time）。
     *
     * <p>面板存储 {@code expire_datetime}（DATETIME），协议鉴权 {@link SysAiAppKey#getSysAiAppKeyExpireTime()}
     * 读取 {@code expire_time}（epoch 秒），两者不同源导致面板过期限在协议端不生效。
     * 写入时同时补上 max_use 与 use_count，保证配额/过期限列与面板一致。</p>
     *
     * @param id                面板 AppKey 主键
     * @param expireEpochSecond 过期时间（epoch 秒），null 表示永不
     * @param maxUse            次数上限，null 表示不限
     */
    void updateExpireCompatible(Long id, Long expireEpochSecond, Long maxUse);

    /**
     * 更新 AppKey 的 Token 消耗累计值。
     *
     * <p>每次对话成功后异步调用，将本次消耗的 Token 数累加到
     * {@code sys_ai_app_key_total_tokens} 字段。</p>
     *
     * @param appKey  AppKey 值
     * @param tokens  本次消耗的 Token 数
     */
    void updateTokenQuotaByAppKey(String appKey, long tokens);
}
