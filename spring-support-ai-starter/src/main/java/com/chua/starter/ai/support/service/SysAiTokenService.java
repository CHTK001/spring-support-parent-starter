package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiToken;

import java.math.BigDecimal;
import java.util.List;

/**
* AI 令牌服务——管理 令牌 的创建、校验和用量统计。
* <p>
* 令牌 绑定分组，每次请求校验 令牌 有效性并按分组授权访问模型。
*
* @author CH
* @since 4.0.0
 */
public interface SysAiTokenService {

    /**
    * 列出所有未删除的令牌。
    *
    * @return 令牌列表
     */
    List<SysAiToken> list();

    /**
    * 根据分组 标识 查询令牌列表。
    *
    * @param groupId 分组 标识
    * @return 令牌列表
     */
    List<SysAiToken> listByGroupId(Integer groupId);

    /**
    * 保存令牌（自动生成 sk- 格式密钥和哈希）。
    *
    * @param token 令牌实体
    * @return 保存后的令牌（含明文密钥）
     */
    SysAiToken save(SysAiToken token);

    /**
    * 更新令牌。
    *
    * @param token 令牌实体
    * @return 是否成功
     */
    boolean update(SysAiToken token);

    /**
    * 逻辑删除令牌。
    *
    * @param tokenId 令牌 标识
    * @return 是否成功
     */
    boolean deleteById(Integer tokenId);

    /**
    * 按令牌密钥查找。
    *
    * @param tokenKey 令牌密钥（sk-xxx）
    * @return 令牌实体（不存在返回 空）
     */
    SysAiToken findByTokenKey(String tokenKey);

    /**
    * 验证令牌有效性（状态、过期、分组）。
    *
    * @param tokenKey 令牌密钥
    * @param groupId  请求的分组 标识（空 表示不校验分组）
    * @return 有效的令牌，无效/过期返回 空
     */
    SysAiToken verify(String tokenKey, Integer groupId);

    /**
    * 获取令牌绑定的分组 标识。
    *
    * @param tokenKey 令牌密钥
    * @return 分组 标识（不存在返回 空）
     */
    Integer getGroupIdByTokenKey(String tokenKey);

    /**
    * 记录令牌使用信息（异步执行）。
    *
    * @param tokenKey       令牌密钥
    * @param promptTokens   输入 令牌 数
    * @param completionTokens 输出 令牌 数
    * @param cost           费用
     */
    void recordUsage(String tokenKey, int promptTokens, int completionTokens, BigDecimal cost);

    /**
    * 检查令牌是否触发限流。
    *
    * @param tokenKey 令牌密钥
    * @return true 表示未触发限流，false 表示已触发
     */
    boolean checkRateLimit(String tokenKey);

    /**
    * 按分组检查限流：取该分组下第一个启用的 令牌 执行限流判定。
    *
    * <p>用于 AppKey/Bearer 协议鉴权链路，将 Token 的 rate_limit 能力接入运行时。
    * 分组无 令牌 时不限流（放行），避免阻断未配置计量计费的分组。</p>
    *
    * @param groupId 分组 标识（空 直接放行）
    * @return true 表示未触发限流，false 表示已触发
     */
    boolean checkRateLimitByGroup(Integer groupId);
}
