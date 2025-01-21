package com.chua.report.server.starter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.constant.Projects;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.report.server.starter.entity.MonitorNginxConfig;
import com.chua.report.server.starter.entity.MonitorNginxEvent;
import com.chua.report.server.starter.mapper.*;
import com.chua.report.server.starter.ngxin.*;
import com.chua.report.server.starter.pojo.NginxInclude;
import com.chua.report.server.starter.service.MonitorNginxConfigService;
import com.chua.report.server.starter.service.MonitorNginxEventService;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

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
    private final MonitorNginxEventService monitorNginxEventService;

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
    public Boolean analyzeConfig(Integer nginxConfigId) {
        MonitorNginxConfig monitorNginxConfig = baseMapper.selectById(nginxConfigId);
        if(StringUtils.isBlank(monitorNginxConfig.getMonitorNginxConfigPath())) {
            return null;
        }

        File file = new File(monitorNginxConfig.getMonitorNginxConfigPath());
        if(!file.exists()) {
            return false;
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            NginxDisAssembly disAssembly = new NginxDisAssembly(monitorNginxConfig);
            SpringBeanUtils.autowireBean(disAssembly);
            return disAssembly.handle(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List configFormInclude(Integer nginxConfigId) {
        MonitorNginxConfig monitorNginxConfig = baseMapper.selectById(nginxConfigId);
        if(StringUtils.isBlank(monitorNginxConfig.getMonitorNginxConfigPath())) {
            return Collections.emptyList();
        }
        String fullPath = NginxDisAssembly.getFullPath(monitorNginxConfig.getMonitorNginxConfigPath());
        File file = new File(fullPath);
        if(!file.isDirectory() || null == file.listFiles()) {
            return Collections.emptyList();
        }

        List<NginxInclude> rs = new LinkedList<>();
        for (File listFile : Objects.requireNonNull(file.listFiles())) {
            try (FileInputStream fis = new FileInputStream(listFile)) {
                rs.add(new NginxInclude(listFile.getName(), IoUtils.toString(fis, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return rs;
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

    @Override
    public ReturnPageResult<MonitorNginxConfig> pageForConfig(Query<MonitorNginxConfig> query) {
        Page<MonitorNginxConfig> monitorNginxConfigPage = baseMapper.selectPage(query.createPage(), query.mpjLambda());
        List<MonitorNginxConfig> records = monitorNginxConfigPage.getRecords();
        if(CollectionUtils.isNotEmpty(records)) {
            Set<Integer> ids = records.stream().map(MonitorNginxConfig::getMonitorNginxConfigId).collect(Collectors.toSet());
            List<MonitorNginxEvent> list = monitorNginxEventService.list(Wrappers.<MonitorNginxEvent>lambdaQuery().in(MonitorNginxEvent::getMonitorNginxConfigId, ids));
            Map<Integer, MonitorNginxEvent> monitorNginxEventMap = list.stream().collect(Collectors.toMap(MonitorNginxEvent::getMonitorNginxConfigId, it -> it));
            for (MonitorNginxConfig monitorNginxConfig : records) {
                monitorNginxConfig.setEvents(monitorNginxEventMap.get(monitorNginxConfig.getMonitorNginxConfigId()));
                monitorNginxConfig.setRunning(findPid(monitorNginxConfig.getMonitorNginxConfigPid()));
            }
        }
        return ReturnPageResultUtils.ok(monitorNginxConfigPage);
    }

    private Boolean findPid(String monitorNginxConfigPid) {
        try (FileInputStream fis = new FileInputStream(new File(monitorNginxConfigPid, "nginx.pid"))) {
            String pid = IoUtils.toString(fis, StandardCharsets.UTF_8);
            return Projects.isWindows()?
                    checkWindowPidExists(pid):
                    checkLinuxPidExists(pid);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean update(MonitorNginxConfig nginxConfig) {
        return baseMapper.updateById(nginxConfig) > 0;
    }

    @Override
    public MonitorNginxConfig saveForConfig(MonitorNginxConfig nginxConfig) {
        baseMapper.insert(nginxConfig);
        return nginxConfig;
    }

    @Override
    public MonitorNginxConfig getForConfig(Integer monitorNginxConfigId) {
        if(null == monitorNginxConfigId || monitorNginxConfigId <= 0) {
            return null;
        }
        return baseMapper.selectById(monitorNginxConfigId);
    }

    public static boolean checkLinuxPidExists(String pid) {
        Process process = null;
        try {
            // 构造命令：kill -0 <PID>
            String command = "kill -0 " + pid;

            // 执行命令
            process = Runtime.getRuntime().exec(command);

            // 等待命令执行完成
            int exitCode = process.waitFor();
            return exitCode == 0; // 如果退出码为 0，表示 PID 存在
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            IoUtils.closeQuietly(process);
        }
    }
    public static boolean checkWindowPidExists(String pid) {
        Process process = null;
        try {
            // 构造命令：tasklist /FI "PID eq <PID>"
            String command = "tasklist /FI \"PID eq " + pid + "\"";

            // 执行命令
            process = Runtime.getRuntime().exec(command);

            // 读取命令输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("INFO: No tasks are running")) {
                        return false; // 如果输出中包含 "INFO: No tasks are running"，表示 PID 不存在
                    }
                }
            }
            // 等待命令执行完成
            int exitCode = process.waitFor();
            return exitCode == 0; // 如果退出码为 0，表示命令执行成功，PID 存在
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            IoUtils.closeQuietly(process);
        }
    }
}
