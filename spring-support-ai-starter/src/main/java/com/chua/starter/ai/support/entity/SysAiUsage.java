package com.chua.starter.ai.support.entity;

import com.chua.starter.mybatis.pojo.SysBase;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 用量记录表。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_ai_usage")
public class SysAiUsage extends SysBase {

    /**
      * 主键 标识
     */
    @TableId(type = IdType.AUTO)
    private Long sysAiUsageId; // sysaiusageid

    /**
      * 分组：对话 / 镜像 / 视频 / colorize / vision / rag
     */
    private String sysAiUsageGroupName;

    /**
      * 服务商：openai / zhipu / 百度云 等
     */
    private String sysAiUsageProvider;

    /**
     * 模型名称
     */
    private String sysAiUsageModel;

    /**
       * 调用类型：同步 / 异步
     */
    private String sysAiUsageCallType;

    /**
      * 输入 令牌 数
     */
    private Integer sysAiUsagePromptTokens;

    /**
      * 输出 令牌 数
     */
    private Integer sysAiUsageCompletionTokens;

    /**
      * 总 令牌 数
     */
    private Integer sysAiUsageTotalTokens;

    /**
     * 输入费用（单位：元）
     */
    private Double sysAiUsageInputCost;

    /**
     * 输出费用（单位：元）
     */
    private Double sysAiUsageOutputCost;

    /**
     * 总费用（单位：元）
     */
    private Double sysAiUsageTotalCost;

    /**
      * 请求 标识（关联调用日志）
     */
    private String sysAiUsageRequestId;

    /**
     * 用户标识（可选）
     */
    private String sysAiUsageUserId;

    /**
     * 调用时间
     */
    private LocalDateTime sysAiUsageCallTime;

    /** 推理强度 */
    private String sysAiUsageInferenceStrength;
    /** 端点URL */
    private String sysAiUsageEndpoint;
    /** 请求IP */
    private String sysAiUsageIp;
    /** IP 物理地址（省/市，来自 IP 定位） */
    private String sysAiUsageLocation;
    /** 请求类型: 对话/完成/嵌入 */
    private String sysAiUsageType;
    /** 计费模式: 薪酬_as_you_Go/subscription */
    private String sysAiUsageBillingMode;
    /** 缓存读取 令牌 */
    private Integer sysAiUsageCachedTokens;
    /** 缓存读取成本 */
    private Double sysAiUsageCachedCost;
    /** 输入单价(每1M 令牌) */
    @com.baomidou.mybatisplus.annotation.TableField("sys_ai_usage_input_price_per_1m")
    private Double sysAiUsageInputPricePer1M; // sysaiusage输入priceper1m
    /** 输出单价(每1M 令牌) */
    @com.baomidou.mybatisplus.annotation.TableField("sys_ai_usage_output_price_per_1m")
    private Double sysAiUsageOutputPricePer1M; // sysaiusage输出priceper1m
    /** 缓存读取单价(每1M 令牌) */
    @com.baomidou.mybatisplus.annotation.TableField("sys_ai_usage_cache_read_price_per_1m")
    private Double sysAiUsageCacheReadPricePer1M; // sysaiusage缓存读取priceper1m
    /** 服务档位 */
    private String sysAiUsageServiceTier;
    /** 倍率 */
    private Double sysAiUsageRatio;
    /** 原始费用(未折扣) */
    private Double sysAiUsageOriginalCost;
    /** 首字延迟(ms) */
    private Long sysAiUsageFirstTokenLatency;
    /** 总耗时(ms) */
    private Long sysAiUsageTotalLatency;

    /**
     * 获取分组名称，为空时返回默认值"默认"，保证前端展示有兜底。
     *
     * @return 分组名称，null 时返回"默认"
     */
    public String getSysAiUsageGroupName() {
        return sysAiUsageGroupName == null ? "默认" : sysAiUsageGroupName;
    }

    /**
     * 获取推理强度，为空时返回默认值"默认"，保证前端展示有兜底。
     *
     * @return 推理强度，null 时返回"默认"
     */
    public String getSysAiUsageInferenceStrength() {
        return sysAiUsageInferenceStrength == null ? "默认" : sysAiUsageInferenceStrength;
    }

    /**
      * 获取端点 URL，为空时返回默认值 "/v1/对话/completions"。
     *
     * @return 端点 URL，空 时返回 "/v1/对话/completions"
     */
    public String getSysAiUsageEndpoint() {
        return sysAiUsageEndpoint == null ? "/v1/chat/completions" : sysAiUsageEndpoint;
    }

    /**
     * 获取请求 IP，为空时返回默认值 "127.0.0.1"。
     *
     * @return 请求 IP，空 时返回 "127.0.0.1"
     */
    public String getSysAiUsageIp() {
        return sysAiUsageIp == null ? "127.0.0.1" : sysAiUsageIp;
    }

    /**
     * 获取请求类型，为空时返回默认值"默认"。
     *
     * @return 请求类型，null 时返回"默认"
     */
    public String getSysAiUsageType() {
        return sysAiUsageType == null ? "默认" : sysAiUsageType;
    }

    /**
     * 获取计费模式，为空时返回默认值"默认"。
     *
     * @return 计费模式，null 时返回"默认"
     */
    public String getSysAiUsageBillingMode() {
        return sysAiUsageBillingMode == null ? "默认" : sysAiUsageBillingMode;
    }
}
