package com.chua.starter.spider.support.service;

import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;

import java.util.List;
import java.util.Map;

/**
 * 爬虫 AI 集成服务接口。
 *
 * @author CH
 */
public interface SpiderAiService {

    /**
     * 对整条编排进行完整性审查。
     *
     * @param taskId      任务 ID
     * @param recentLogs  最近日志（可为 null）
     * @param failSamples 失败样本（可为 null）
     * @return 审查报告
     */
    AiReviewResult reviewTask(Long taskId, List<String> recentLogs, List<String> failSamples);

    /**
     * 获取节点级 AI 建议。
     *
     * @param taskId   任务 ID
     * @param nodeId   节点 ID
     * @param nodeType 节点类型
     * @return 建议内容
     */
    AiNodeSuggestion suggestNode(Long taskId, String nodeId, SpiderNodeType nodeType);

    /**
     * 将 AI 建议应用到节点配置。
     *
     * @param taskId     任务 ID
     * @param nodeId     节点 ID
     * @param suggestion 建议内容
     */
    void applyNodeSuggestion(Long taskId, String nodeId, AiNodeSuggestion suggestion);

    /**
     * 查询 AI 服务是否可用。
     */
    boolean isAvailable();

    // ── 内嵌 DTO ─────────────────────────────────────────────────────────────

    record AiReviewResult(boolean success, String report, String degradeReason) {
        public static AiReviewResult degrade(String reason) {
            return new AiReviewResult(false, null, reason);
        }
    }

    record AiNodeSuggestion(String nodeId, SpiderNodeType nodeType,
                            Map<String, Object> suggestedConfig, String explanation) {
        public static AiNodeSuggestion degrade(String nodeId, SpiderNodeType nodeType, String reason) {
            return new AiNodeSuggestion(nodeId, nodeType, Map.of(), reason);
        }
    }
}
