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
     * 创建时间
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "次数(min)")
    @Schema(description = "次数(min)")
    private Long count;
}

