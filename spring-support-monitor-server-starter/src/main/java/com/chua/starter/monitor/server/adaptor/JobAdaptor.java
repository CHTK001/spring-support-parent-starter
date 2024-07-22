package com.chua.starter.monitor.server.adaptor;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.entity.MonitorJobLog;
import com.chua.starter.monitor.server.service.MonitorJobLogService;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
@Spi("job")
public class JobAdaptor implements Adaptor<MonitorRequest> {

    @AutoInject
    private MonitorJobLogService monitorJobLogService;
    @Override
    public void doAdaptor(MonitorRequest monitorRequest) {
        monitorJobLogService.update(Wrappers.<MonitorJobLog>lambdaUpdate()
                .set(MonitorJobLog::getJobLogExecuteCode, monitorRequest.getCode())
                .set(MonitorJobLog::getJobLogTriggerMsg, monitorRequest.getMsg())
                .eq(MonitorJobLog::getJobLogId, monitorRequest.getData())
        );
    }

    @Override
    public Class<MonitorRequest> getType() {
        return MonitorRequest.class;
    }

}
