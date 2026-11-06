package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集告警规则。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("collect_alert_rule")
public class CollectAlertRule {

    @TableId(value = "collect_alert_rule_id", type = IdType.AUTO)
    private Integer collectAlertRuleId;

    /** 所属流程 ID，为 null 时表示全局规则 */
    private Integer collectFlowId;

    /** 规则名称 */
    private String collectAlertRuleName;

    /**
     * 告警条件类型。
     * FAILURE_COUNT  : 连续失败次数 >= threshold
     * ZERO_OUTPUT   : 单次产出为 0
     * RECORDS_DROP  : 单次产出 < threshold（前次产出 * ratio）
     * NODE_TIMEOUT  : 节点耗时 > threshold ms
     * ERROR_RATE    : 错误率 > threshold %
     */
    private String alertType;

    /** 告警阈值，含义随 alertType 而定 */
    private Integer threshold;

    /**
     * 告警时机。
     * ALWAYS    : 每次触发都告警
     * ONCE      : 只告警一次，规则自动禁用
     * REPEAT    : 重复告警，但有静默期（当前不支持）
     */
    private String alertTiming;

    /** 是否启用 */
    private Integer alertEnabled;

    /** 最后触发时间 */
    private LocalDateTime lastAlertTime;

    /** 最后触发时发送的结果（成功/失败） */
    private String lastAlertResult;

    private String createName;
    private LocalDateTime createTime;
    private String updateName;
    private LocalDateTime updateTime;
}
