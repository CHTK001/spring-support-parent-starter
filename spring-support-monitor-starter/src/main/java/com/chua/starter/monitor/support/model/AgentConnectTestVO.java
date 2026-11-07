// [P3C BL-边界 豁免]

package com.chua.starter.monitor.support.model;

/**
 * Agent 连通性测试结果
 *
 * @param available 是否连通
 * @param version   连通时返回的 Agent 版本，不可达为空
 * @param msg       结论文案
 * @author CH
 * @since 4.0.0.42
 */
public record AgentConnectTestVO(boolean available, String version, String msg) {
}
