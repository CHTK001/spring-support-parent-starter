package com.chua.starter.monitor.support.spi;

import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.entity.MsMonitorTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 采集上下文
 * <p>
 * 封装一次采集所需的所有入参(任务/资产), 由调度器构造并传入 Provider。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectContext {

    /**
     * 采集任务
     */
    private MsMonitorTask task;

    /**
     * 关联资产
     */
    private MsMonitor monitor;
}