package com.chua.starter.monitor.support.model;

import com.chua.common.support.ai.AiUsage;

/**
 * 主机指标智能分析结果
 *
 * @param hostId   主机资产 ID
 * @param hostName 主机资产名称
 * @param content  模型输出文本
 * @param model    实际使用的模型名
 * @param usage    模型回传的令牌用量，客户端未回传时为空
 * @author CH
 * @since 4.0.0.42
 */
public record AiHostAnalyzeVO(
        Long hostId,
        String hostName,
        String content,
        String model,
        AiUsage usage
) {
}
