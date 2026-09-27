package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 服务访问令牌实体，记录令牌归属、有效期以及请求和令牌用量限制。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_service_token")
public class SysAiServiceToken {
    @TableId(type = IdType.AUTO)
    private Long tokenId;
    private String instanceId;
    private String token;
    private String tokenName;
    private Integer enabled;
    private String userId;
    private LocalDateTime createTime;

    /**
     * 令牌类型: permanent(永久) / temporary(临时)
     */
    private String tokenType;
    /**
     * 过期时间（临时令牌），为空表示不过期
     */
    private LocalDateTime expiresAt;
    /**
     * 最大请求次数，为空表示不限
     */
    private Long maxRequests;
    /**
     * 已用请求次数
     */
    private Long usedRequests;
    /**
     * 最大可用 Token 数量，为空表示不限
     */
    private Long maxTokens;
    /**
     * 已用 Token 数量
     */
    private Long usedTokens;
}
