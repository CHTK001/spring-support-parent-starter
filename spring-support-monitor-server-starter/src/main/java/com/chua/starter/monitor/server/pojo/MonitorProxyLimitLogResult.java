package com.chua.starter.monitor.server.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.monitor.server.entity.MonitorProxyLimitLog;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 该类用于封装监控代理限制日志的结果。
 * <p>
 * 通过此类，可以方便地获取和处理监控过程中关于限流的日志信息。
 * 目前，此类为空，但为未来的扩展预留了空间。
 *
 * @author CH
 * @since 2024/6/26
 */
@ApiModel(description = "限流日志")
@Schema(description = "限流日志")
@Data
@TableName(value = "monitor_proxy_limit_log")
public class MonitorProxyLimitLogResult extends MonitorProxyLimitLog {

    /**
     * 总次数
     * 记录某个操作的总发生次数，单位为分钟。
     * 用于统计和分析操作的频率。
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "总次数(/min)")
    private Integer count;

    /**
     * 总允许次数
     * 设定某个操作在单位时间内允许的最大次数。
     * 用于限制操作的频率，防止过度使用或滥用。
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "总允许次数(/min)")
    private Integer allowCount;

    /**
     * 总拒绝次数
     * 记录在单位时间内被拒绝的操作次数。
     * 用于监控操作的限制效果，评估系统的访问控制能力。
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "总拒绝次数(/min)")
    private Integer denyCount;

    /**
     * 总警告次数
     * 记录在单位时间内产生警告的操作次数。
     * 用于提示用户或系统管理员关注某些操作的异常或潜在风险。
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "总警告次数(/min)")
    private Integer warnCount;
}

