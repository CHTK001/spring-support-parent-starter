package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.session.Session;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.FileUtils;
import com.chua.starter.monitor.server.entity.MonitorSysGen;
import com.chua.starter.monitor.server.mapper.MonitorSysGenMapper;
import com.chua.starter.monitor.server.properties.GenProperties;
import com.chua.starter.monitor.server.service.MonitorGenBackupService;
import com.chua.starter.monitor.server.service.MonitorSysGenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class MonitorSysGenServiceImpl extends ServiceImpl<MonitorSysGenMapper, MonitorSysGen> implements MonitorSysGenService {
    final MonitorGenBackupService monitorGenBackupService;
    final GenProperties genProperties;
    @Override
    public void updateFor(MonitorSysGen newSysGen, MonitorSysGen oldSysGen) {
        baseMapper.updateById(newSysGen);
        //更新备份
        Integer genBackupStatus = newSysGen.getGenBackupStatus();
        //备份已开启
        if(newSysGen.userHasChange(oldSysGen)) {
            if(null != genBackupStatus && genBackupStatus == 1) {
                monitorGenBackupService.stop(oldSysGen);
                monitorGenBackupService.start(newSysGen);
            }
        }
        monitorGenBackupService.upgrade(newSysGen);
    }

    @Override
    public Boolean deleteFor(String id, MonitorSysGen oldSysGen) {
        monitorGenBackupService.stop(oldSysGen);
        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), id));
        try {
            FileUtils.forceDelete(mkdir);
        } catch (IOException ignored) {
        }
        ServiceProvider.of(Session.class).closeKeepExtension(oldSysGen.getGenId() + "");
        baseMapper.deleteById(oldSysGen.getGenId());
        return true;
    }
}
