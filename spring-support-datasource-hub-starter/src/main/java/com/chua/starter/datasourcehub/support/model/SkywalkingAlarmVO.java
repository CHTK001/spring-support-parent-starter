package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 告警列表项。
 *
 * @param id          告警 ID
 * @param name        触发告警的<b>实体名</b>（服务名 / 「实例 of 服务」/「端点 in 服务」）
 * @param message     告警内容（规则模板渲染后的文案）
 * @param startTime   触发毫秒戳
 * @param scope       作用域（Service / Endpoint / ServiceInstance …）
 * @param serviceName 相关服务（从事件来源反查，可空）
 * @param ruleName    规则名。query-protocol 未暴露该字段，通常为空
 * @param events      事件明细（已格式化成「名称: 说明」）
 * @param tags        规则上配置的标签
 * @author CH
 * @since 4.0.0.51
 */
public record SkywalkingAlarmVO(String id,
                                String name,
                                String message,
                                long startTime,
                                String scope,
                                String serviceName,
                                String ruleName,
                                List<String> events,
                                List<TagVO> tags) {
}
