package com.chua.starter.account.manage.service;

import com.chua.starter.account.manage.store.SysAccountAppKey;

import java.util.List;

/**
 * 账号模块外部访问密钥服务。
 *
 * <p>只服务于本模块的账号能力（设备 / 脚本 / 外部系统调用账号接口），
 * 与 AI 的模型计费密钥是两套独立数据，互不读写。</p>
 *
 * <p>Secret 只在创建与轮换时明文回显一次，之后一律以密文落库、接口不回显。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public interface SysAccountAppKeyService {

    /**
     * 查询当前用户可见的密钥。
     *
     * @param keyword 名称 / Key 模糊词（可空）
     * @return 密钥列表（不含 Secret）
     */
    List<SysAccountAppKey> list(String keyword);

    /**
     * 新增密钥，返回时带一次性明文 Secret。
     *
     * @param input 名称、过期时间、IP 白名单、备注
     * @return 落库后的密钥（含 {@code plainSecret}）
     */
    SysAccountAppKey create(SysAccountAppKey input);

    /**
     * 更新密钥的可变属性（名称 / 状态 / 过期时间 / 白名单 / 备注）。
     *
     * @param input 必须带 {@code appKeyId}
     * @return 更新后的密钥
     */
    SysAccountAppKey update(SysAccountAppKey input);

    /**
     * 轮换 Secret，返回时带一次性明文 Secret。
     *
     * @param appKeyId 密钥标识
     * @return 更新后的密钥（含 {@code plainSecret}）
     */
    SysAccountAppKey rotate(String appKeyId);

    /**
     * 逻辑删除密钥。
     *
     * @param appKeyId 密钥标识
     * @return 是否删除成功
     */
    boolean delete(String appKeyId);

    /**
     * 校验访问凭据并刷新最近使用时间。
     *
     * @param appKey    访问 Key
     * @param secret    明文 Secret
     * @param remoteIp  调用方 IP（可空；配置白名单时参与校验）
     * @return true 表示凭据有效
     */
    boolean authenticate(String appKey, String secret, String remoteIp);
}
