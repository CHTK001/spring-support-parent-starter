package com.chua.starter.account.manage.service;

import com.chua.starter.account.manage.store.SysAccountSecret;

import java.util.List;

/**
 * 账号密钥库服务。
 *
 * <p>密钥是账号模块最底层的凭据能力：账号只引用 {@code secretId}，
 * 后续 server 等模块也可以直接按 {@code secretId} 取用，不必各自再存一份。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface SysAccountSecretService {

    /**
     * 当前用户可见的密钥列表。
     *
     * @param provider 厂商过滤（可空）
     * @param keyword  名称 / AccessKey 模糊词（可空）
     * @return 密钥列表（不含明文 Secret）
     */
    List<SysAccountSecret> list(String provider, String keyword);

    /**
     * 按类型过滤的密钥列表（账号引用密钥时按类型挑选）。
     *
     * <p>类型支持多值存储（{@code sms,storage}），过滤按 token 命中：
     * 只要密钥的类型里包含该类型就会被返回。</p>
     *
     * @param secretType 密钥类型（sms / storage / payment / mail / api / other，可空表示不限）
     * @return 密钥列表（不含明文 Secret）
     */
    List<SysAccountSecret> listByType(String secretType);

    /**
     * 密钥详情。
     *
     * @param secretId 密钥标识
     * @return 密钥（不含明文 Secret）
     */
    SysAccountSecret get(String secretId);

    /**
     * 新增密钥。
     *
     * @param input 密钥内容（{@code secret} 传明文）
     * @return 落库后的密钥（不含明文 Secret）
     */
    SysAccountSecret create(SysAccountSecret input);

    /**
     * 更新密钥（{@code secret} 留空或掩码表示不修改）。
     *
     * @param input 密钥内容
     * @return 更新后的密钥
     */
    SysAccountSecret update(SysAccountSecret input);

    /**
     * 删除密钥（逻辑删除）。
     *
     * @param secretId 密钥标识
     * @return 是否删除成功
     */
    boolean delete(String secretId);

    /**
     * 取密钥明文 Secret，供发信 / 同步模板等内部编排使用。
     *
     * @param secretId 密钥标识
     * @return 明文 Secret；不存在返回 {@code null}
     */
    String resolveSecret(String secretId);

    /**
     * 取密钥实体（含访问标识），并校验当前用户可见。
     *
     * @param secretId 密钥标识
     * @return 密钥
     */
    SysAccountSecret requireVisible(String secretId);
}
