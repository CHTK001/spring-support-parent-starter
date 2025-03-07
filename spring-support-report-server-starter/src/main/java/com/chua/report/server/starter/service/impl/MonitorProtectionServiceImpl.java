package com.chua.report.server.starter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.utils.CmdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.report.server.starter.entity.MonitorProtection;
import com.chua.report.server.starter.mapper.MonitorProtectionMapper;
import com.chua.report.server.starter.service.MonitorProtectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class MonitorProtectionServiceImpl extends ServiceImpl<MonitorProtectionMapper, MonitorProtection> implements MonitorProtectionService, InitializingBean, DisposableBean {

    final ExecutorService executorService = ThreadUtils.newFixedThreadExecutor(1, "monitor-daemon");
    final ScheduledExecutorService dataExecutorService = ThreadUtils.newScheduledThreadPoolExecutor(1, "monitor-daemon-data-checker");
    final AtomicBoolean running = new AtomicBoolean(false);

    final Queue<MonitorProtection> queue = new LinkedBlockingQueue<>(10240);

    @Override
    public void afterPropertiesSet() throws Exception {

        running.set(true);
        registerDataExecutor();
        registerTaskExecutor();
    }

    /**
     * 注册任务执行器
     */
    private void registerTaskExecutor() {
        executorService.submit(() -> {
            while (running.get()) {
                ThreadUtils.sleep(1000);
                MonitorProtection poll = queue.poll();
                if (null == poll || StringUtils.isEmpty(poll.getMonitorProtectionShell())) {
                    ThreadUtils.sleep(0);
                    continue;
                }
                Thread.ofVirtual()
                        .start(() -> {
                            try {
                                checkDaemon(poll);
                            } catch (Exception ignored) {
                            }
                            queue.add(poll);
                        });
            }
        });
    }

    /**
     * 检查守护进程
     *
     * @param poll 守护进程
     */
    private void checkDaemon(MonitorProtection poll) {
        Integer monitorProtectionPid = poll.getMonitorProtectionPid();
        if (null == monitorProtectionPid || monitorProtectionPid <= 0 || monitorProtectionPid > 65535) {
            return;
        }

        if (NetUtils.checkPidExists(String.valueOf(monitorProtectionPid))) {
            return;
        }

        runShell(poll.getMonitorProtectionShell());
    }

    /**
     * 运行shell
     *
     * @param monitorProtectionShell shell
     */
    private void runShell(String monitorProtectionShell) {
        String exec = CmdUtils.exec(monitorProtectionShell);
        log.info("执行shell: {}", exec);
    }

    /**
     * 注册数据执行器
     */
    private void registerDataExecutor() {
        dataExecutorService.scheduleAtFixedRate(() -> {
            try {
                List<MonitorProtection> list = list(Wrappers.<MonitorProtection>lambdaQuery().eq(MonitorProtection::getMonitorProtectionStatus, 0));
                queue.clear();
                queue.addAll(list);
            } catch (Exception ignored) {
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    @Override
    public void destroy() throws Exception {
        running.set(false);
        ThreadUtils.closeQuietly(executorService);
    }
}
