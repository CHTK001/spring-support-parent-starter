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
     *
     * <p>历史字段，仅作兼容保留；配额语义以 {@link #quotaType} 为准
     * （按时间签发时同步写 temporary，便于老代码读取）。</p>
     */
    private String tokenType;
    /**
     * 过期时间（按时间配额），为空表示不过期
     */
    private LocalDateTime expiresAt;
    /**
     * 最大请求次数（按次配额），为空表示不限
     */
    private Long maxRequests;
    /**
     * 已用请求次数
     */
    private Long usedRequests;
    /**
     * 余额总量（按余额配额，单位 Token），为空表示不限
     */
    private Long maxTokens;
    /**
     * 已用 Token 数量（余额模式即「已消耗」，余额 = maxTokens - usedTokens）
     */
    private Long usedTokens;
    /**
     * 配额方式：time 按时间 / count 按次数 / balance 按余额（配总量）。
     *
     * <p>它是这三种额度里**主约束**的声明，前端据此渲染配额表单与余量尺；
     * 鉴权侧仍然对已填写的各项额度逐条判定（填了就生效），因此老数据
     * （quota_type 为空）也照样按 expiresAt / maxRequests / maxTokens 生效。</p>
     */
    private String quotaType;
    /**
     * 允许调用的模型编码，英文逗号分隔（如 {@code gpt-4o,claude-sonnet-4}）。
     *
     * <p>为空或 NULL 表示不限制模型（沿用实例的协议与默认模型）。编码取自
     * 「模型管理」，请求里的 model 与之做忽略大小写的等值匹配；命中别名同样放行
     * （别名在写入时已展开，见 {@code ServiceApiController#normalizeModelIds}）。</p>
     */
    private String modelIds;
    /**
     * 最近一次成功调用时间，供台账显示「这把 Key 还有人用吗」
     */
    private LocalDateTime lastUsedAt;
}
