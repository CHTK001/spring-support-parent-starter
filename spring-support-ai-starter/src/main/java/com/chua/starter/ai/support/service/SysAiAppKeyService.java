package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiAppKey;

import java.util.List;
import java.util.Optional;

/**
 * AI appKey 服务接口。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface SysAiAppKeyService {

    /**
     * 查询全部 appKey。
     *
     * @return AppKey 列表
     */
    List<SysAiAppKey> list();

    /**
     * 新增 appKey。
     *
     * @param appKey appKey 实体
     * @return 保存后的 appKey
     */
    SysAiAppKey save(SysAiAppKey appKey);

    /**
     * 更新 appKey。
     *
     * @param appKey appKey 实体
     * @return 是否成功
     */
    boolean update(SysAiAppKey appKey);

    /**
     * 按 标识 删除 appKey。
     *
     * @param id appKey 标识
     * @return 是否成功
     */
    boolean deleteById(Integer id);

    /**
     * 按 appKey 查询。
     *
     * @param appKey appKey
     * @return AppKey 实体（可选）
     */
    default Optional<SysAiAppKey> findByAppKey(String appKey) {
        return Optional.ofNullable(getByAppKey(appKey));
    }

    /**
     * 获取byappKey。
     * @param appKey appKey
     * @return 获取byappKey的结果
     */
    SysAiAppKey getByAppKey(String appKey);

    /**
     * 获取群体idbyappKey。
     * @param appKey appKey
     * @return 获取群体idbyappKey的结果
     */
    Integer getGroupIdByAppKey(String appKey);

    /**
     * 验证。
     * @param appKey appKey
     * @param secret secret
     * @param ip ip
     * @return 验证的结果
     */
    boolean verify(String appKey, String secret, String ip);

    /**
     * 仅校验 appKey 是否有效（状态、过期、IP 白名单）。
     * <p>用于 Bearer/Header 单项凭证鉴权场景，AppKey 即完整凭证，不要求额外的 secret。</p>
     *
     * @param appKey appKey
     * @param ip     客户端 IP（可为空）
     * @return 是否有效
     */
    boolean verifyAppKeyOnly(String appKey, String ip);

    /**
     * 更新use信息byappKey。
     * @param appKey appKey
     */
    void updateUseInfoByAppKey(String appKey);

    /**
     * 更新 appKey 的 令牌 消耗累计值。
     *
     * <p>每次对话成功后异步调用，将本次消耗的 Token 数累加到
     * {@code sys_ai_app_key_total_tokens} 字段。</p>
     *
     * @param appKey  appKey 值
     * @param tokens  本次消耗的 令牌 数
     */
    void updateTokenQuotaByAppKey(String appKey, long tokens);
}
