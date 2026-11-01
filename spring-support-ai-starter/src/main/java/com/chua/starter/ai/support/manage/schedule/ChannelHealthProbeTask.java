package com.chua.starter.ai.support.manage.schedule;

import com.chua.starter.ai.support.service.ChannelHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 渠道健康探针调度任务。
 *
 * <p>对标 Sub2API Channel Monitor：按全局健康 setting（group=health）中的探针间隔
 * 定期检查全部已启用供应商的连接，调用 {@link ChannelHealthService#probeAll()} 更新
 * 状态机（UNKNOWN / ONLINE / WARNING / DOWN / RATE_LIMITED），由路由层消费：
 * 下线的连接自动剔除，恢复后自动上浮。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ChannelHealthProbeTask implements SchedulingConfigurer {

    /**
     * 渠道健康探针服务
     */
    private final ChannelHealthService channelHealthService;

    /**
     * 注册动态调度任务：读健康分组开关与间隔，执行全部探针。
     *
     * @param taskRegistrar 调度任务注册器
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        Trigger trigger = this::nextTriggerTime;
        taskRegistrar.addTriggerTask(this::run, trigger);
    }

    /**
     * 调度入口：探针本身有内部开关，这里按间隔循环执行。
     */
    public void run() {
        log.debug("[ChannelHealthProbe] 渠道探针开始");
        try {
            int probed = channelHealthService.probeAll();
            log.info("[ChannelHealthProbe] 渠道探针完成 connections={}", probed);
        } catch (Exception e) {
            log.warn("[ChannelHealthProbe] 渠道探针失败: {}", e.getMessage());
        }
    }

    /**
     * 计算下次执行时间：读全局配置间隔（最小 30s），未配置时用默认值。
     *
     * @param triggerContext 触发器上下文
     * @return 下次执行时间
     */
    private Date nextTriggerTime(TriggerContext triggerContext) {
        long intervalSec = channelHealthService.intervalSec();
        Date last = triggerContext.lastScheduledExecutionTime();
        if (last == null) {
            return Date.from(java.time.Instant.now().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec)));
        }
        return Date.from(last.toInstant().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec)));
    }
}
