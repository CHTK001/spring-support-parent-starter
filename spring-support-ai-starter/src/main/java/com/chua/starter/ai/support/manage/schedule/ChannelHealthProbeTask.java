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

import java.time.Instant;
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
     * 全局设置读取器（判断按渠道时间轮是否接管探针）
     */
    private final com.chua.starter.ai.support.configuration.client.GlobalSettingReader settingReader;

    /**
     * 按渠道时间轮开关所在分组
     */
    private static final String WHEEL_GROUP = "channel_probe";

    /**
     * 按渠道时间轮开关字段
     */
    private static final String WHEEL_ENABLED = "channel_probe_wheel_enabled";

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
        if (settingReader.getBool(WHEEL_GROUP, WHEEL_ENABLED, true)) {
            // 按渠道时间轮已接管探针节奏，这里不再全量探一遍，避免同一渠道被探两次
            log.debug("[ChannelHealthProbe] 按渠道时间轮已启用，跳过全量探针");
            return;
        }
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
    private Instant nextTriggerTime(TriggerContext triggerContext) {
        long intervalSec = channelHealthService.intervalSec();
        Instant last = triggerContext.lastScheduledExecution();
        if (last == null) {
            return Instant.now().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
        }
        return last.plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
    }
}
