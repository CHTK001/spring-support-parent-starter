package com.chua.report.server.starter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.report.server.starter.entity.MonitorNginxConfig;
import com.chua.report.server.starter.mapper.*;
import com.chua.report.server.starter.ngxin.*;
import com.chua.report.server.starter.service.MonitorNginxConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 *
 * @since 2024/12/29
 * @author CH    
 */
@RequiredArgsConstructor
@Service
public class MonitorNginxConfigServiceImpl extends ServiceImpl<MonitorNginxConfigMapper, MonitorNginxConfig> implements MonitorNginxConfigService{

    final MonitorNginxHttpMapper monitorNginxHttpMapper;
    final MonitorNginxHttpServerMapper monitorNginxHttpServerMapper;
    final MonitorNginxHttpServerLocationMapper monitorNginxHttpServerLocationMapper;
    final MonitorNginxHttpServerLocationHeaderMapper monitorNginxHttpServerLocationHeaderMapper;
    final MonitorNginxUpstreamMapper monitorNginxUpstreamMapper;
    final MonitorNginxEventMapper monitorNginxEventMapper;
    @Override
    public Boolean createConfigString(Integer nginxConfigId) {
        NginxAssembly assembly = new NginxAssembly(baseMapper, monitorNginxHttpMapper, monitorNginxHttpServerMapper, monitorNginxHttpServerLocationMapper, monitorNginxHttpServerLocationHeaderMapper, monitorNginxUpstreamMapper, monitorNginxEventMapper);
        return assembly.handle(nginxConfigId);
    }

    @Override
    public String getConfigString(Integer nginxConfigId) {
        MonitorNginxConfig monitorNginxConfig = baseMapper.selectById(nginxConfigId);
        if(StringUtils.isBlank(monitorNginxConfig.getMonitorNginxConfigPath())) {
            return null;
        }

        File file = new File(monitorNginxConfig.getMonitorNginxConfigPath());
        if(!file.exists()) {
            return null;
        }
        try {
            return IoUtils.toString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean analyzeConfig(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            NginxDisAssembly disAssembly = new NginxDisAssembly(baseMapper, monitorNginxHttpMapper, monitorNginxHttpServerMapper, monitorNginxHttpServerLocationMapper, monitorNginxHttpServerLocationHeaderMapper, monitorNginxUpstreamMapper, monitorNginxEventMapper);
            return disAssembly.handle(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean backup(MonitorNginxConfig nginxConfig) {
        MonitorNginxConfig monitorNginxConfig = baseMapper.selectById(nginxConfig.getMonitorNginxConfigId());
        if(StringUtils.isBlank(monitorNginxConfig.getMonitorNginxConfigPath())) {
            return null;
        }

        File file = new File(monitorNginxConfig.getMonitorNginxConfigPath());
        if(!file.exists()) {
            return null;
        }

        FileUtils.copyFile(file, new File(file.getParentFile(), FileUtils.getBaseName(file) + DateTime.now().toString("yyyyMMdd") + ".conf"));
        return true;
    }

    @Override
    public String stop(Integer monitorNginxConfigId) {
        MonitorNginxConfig monitorNginxConfig = baseMapper.selectById(monitorNginxConfigId);
        NginxStop nginxStop = new NginxStop(monitorNginxConfig);
        return nginxStop.run();
    }

    @Override
    public String start(Integer monitorNginxConfigId) {
        MonitorNginxConfig monitorNginxConfig = baseMapper.selectById(monitorNginxConfigId);
        NginxStart nginxStart = new NginxStart(monitorNginxConfig);
        return nginxStart.run();
    }

    @Override
    public String restart(Integer monitorNginxConfigId) {
        MonitorNginxConfig monitorNginxConfig = baseMapper.selectById(monitorNginxConfigId);
        NginxRestart nginxRestart = new NginxRestart(monitorNginxConfig);
        return nginxRestart.run();
    }

}
