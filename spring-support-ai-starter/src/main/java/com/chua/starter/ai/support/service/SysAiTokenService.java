package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiToken;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 令牌服务——管理 Token 的创建、校验和用量统计。
 * <p>
 * Token 绑定分组，每次请求校验 Token 有效性并按分组授权访问模型。
 *
 * @author CH
 */
public interface SysAiTokenService {

    /**
     * 列出所有未删除的令牌。
     *
     * @return 令牌列表
     */
    List<SysAiToken> list();

    /**
     * 根据分组 ID 查询令牌列表。
     *
     * @param groupId 分组 ID
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
     * @param tokenId 令牌 ID
     * @return 是否成功
     */
    boolean deleteById(Integer tokenId);

    /**
     * 按令牌密钥查找。
     *
     * @param tokenKey 令牌密钥（sk-xxx）
     * @return 令牌实体（不存在返回 null）
     */
    SysAiToken findByTokenKey(String tokenKey);

    /**
     * 验证令牌有效性（状态、过期、分组）。
     *
     * @param tokenKey 令牌密钥
     * @param groupId  请求的分组 ID（null 表示不校验分组）
     * @return 有效的令牌，无效/过期返回 null
     */
    SysAiToken verify(String tokenKey, Integer groupId);

    /**
     * 获取令牌绑定的分组 ID。
     *
     * @param tokenKey 令牌密钥
     * @return 分组 ID（不存在返回 null）
     */
    Integer getGroupIdByTokenKey(String tokenKey);

    /**
     * 记录令牌使用信息（异步执行）。
     *
     * @param tokenKey       令牌密钥
     * @param promptTokens   输入 token 数
     * @param completionTokens 输出 token 数
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
}
