package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ErrorResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.server.entity.MonitorProjectVersion;
import com.chua.starter.monitor.server.mapper.MonitorProjectVersionMapper;
import com.chua.starter.monitor.server.service.MonitorProjectService;
import com.chua.starter.monitor.server.service.MonitorProjectVersionService;
import com.chua.starter.monitor.server.terminal.LogScript;
import com.chua.starter.monitor.server.terminal.StartScript;
import com.chua.starter.monitor.server.terminal.StopScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;

@Service
public class MonitorProjectVersionServiceImpl extends ServiceImpl<MonitorProjectVersionMapper, MonitorProjectVersion> implements MonitorProjectVersionService{

    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private MonitorProjectService monitorProjectService;

    @Resource
    private SocketSessionTemplate socketSessionTemplate;
    @Override
    public ErrorResult<Boolean> start(MonitorProjectVersion entity) {
        MonitorProjectVersion monitorProjectVersion = baseMapper.selectById(entity.getVersionId());
        if(null == monitorProjectVersion) {
            return ErrorResult.of("${data.not-exist:数据不存在}");
        }

        Integer versionStatus = monitorProjectVersion.getVersionStatus();
        if(versionStatus != 0) {
            return ErrorResult.of("${status.script.starting:脚本已启动}");
        }

        monitorProjectVersion.setVersionStatus(1);
        if(baseMapper.updateById(monitorProjectVersion) > 0) {
            try {
                StartScript script = new StartScript(monitorProjectService, socketSessionTemplate);
                script.run(monitorProjectVersion);
            } catch (Exception e) {
                monitorProjectVersion.setVersionStatus(0);
                baseMapper.updateById(monitorProjectVersion);
                throw new RuntimeException(e);
            }
        }
        return ErrorResult.ok();
    }

    @Override
    public ErrorResult<Boolean> stop(MonitorProjectVersion entity) {
        MonitorProjectVersion monitorProjectVersion = baseMapper.selectById(entity.getVersionId());
        if(null == monitorProjectVersion) {
            return ErrorResult.of("${data.not-exist:数据不存在}");
        }

        Integer versionStatus = monitorProjectVersion.getVersionStatus();
        if(versionStatus != 1) {
            return ErrorResult.of("${status.script.starting:脚本已启动}");
        }

        String versionStopScript = monitorProjectVersion.getVersionStopScript();

        monitorProjectVersion.setVersionStatus(0);
        if(baseMapper.updateById(monitorProjectVersion) > 0) {
            if(!StringUtils.isEmpty(versionStopScript)) {
                try {
                    StopScript script = new StopScript(monitorProjectService, socketSessionTemplate);
                    script.run(monitorProjectVersion);
                } catch (Exception e) {
                    monitorProjectVersion.setVersionStatus(1);
                    baseMapper.updateById(monitorProjectVersion);
                    throw new RuntimeException(e);
                }
            }
        }
        return ErrorResult.ok();
    }

    @Override
    public ErrorResult<Boolean> log(MonitorProjectVersion entity) {
        MonitorProjectVersion monitorProjectVersion = baseMapper.selectById(entity.getVersionId());
        if(null == monitorProjectVersion) {
            return ErrorResult.of("${data.not-exist:数据不存在}");
        }

        LogScript script = new LogScript(monitorProjectService, socketSessionTemplate);
        script.run(monitorProjectVersion);
        return ErrorResult.ok();
    }


}
