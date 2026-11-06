package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.message.MessageResponse;
import com.chua.starter.collect.dto.CollectNotifyMessage;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.CollectRunResult;
import com.chua.starter.collect.entity.CollectNotice;
import com.chua.starter.collect.entity.CollectTask;
import com.chua.starter.collect.mapper.CollectNoticeMapper;
import com.chua.starter.collect.notify.CollectNotifyChannel;
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.service.CollectNoticeService;
import com.chua.starter.collect.support.CollectTaskStatus;
import com.chua.starter.collect.support.CollectNotifyTiming;
import com.chua.starter.collect.support.CollectTriggerType;
import com.chua.starter.collect.vo.CollectNotifyChannelVO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 采集执行通知服务实现。
 *
 * <p>渠道表在构造期由 Spring Bean 与 {@code @Spi} 扩展共同填充，Spring 侧优先；
 * 发送统一走单个后台线程，因此邮件或回调卡住都不会拖住任务收尾，也不会与流程线程池抢名额。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class CollectNoticeServiceImpl extends ServiceImpl<CollectNoticeMapper, CollectNotice>
        implements CollectNoticeService {

    /**
     * 启用。
     */
    private static final int ENABLED = 1;

    /**
     * 标题模板默认值。
     */
    private static final String DEFAULT_SUBJECT = "【数据采集】${flowName} ${statusText}";

    /**
     * 正文模板默认值。
     */
    private static final String DEFAULT_CONTENT = "任务号：${taskNo}<br/>流程：${flowName}<br/>状态：${statusText}"
            + "<br/>触发：${trigger}<br/>耗时：${cost}ms<br/>节点：${nodeCount}　抓取：${requestCount}"
            + "　结果：${recordCount}<br/>错误：${error}";

    /**
     * 模板时间格式。
     */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * "最近结果"列宽。
     */
    private static final int RESULT_LIMIT = 500;

    /**
     * 平台配置。
     */
    private final CollectProperties properties;

    /**
     * 渠道标识到渠道实现。
     */
    private final Map<String, CollectNotifyChannel> channels = new LinkedHashMap<>();

    /**
     * 发送线程，单线程串行即可，通知不需要并发。
     */
// [P3C C1 豁免] 场景化创建线程，需文档化
    private final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
        Thread thread = new Thread(runnable, "collect-notify");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 构造通知服务并收集渠道实现。
     *
     * @param properties 平台配置
     * @param provider   Spring 容器内的渠道
     */
    public CollectNoticeServiceImpl(CollectProperties properties, ObjectProvider<CollectNotifyChannel> provider) {
        this.properties = properties;
        provider.orderedStream().forEach(channel -> register(channel));
        try {
            ServiceProvider.of(CollectNotifyChannel.class).collect(this::register);
        } catch (Exception ex) {
            log.debug("[collect] SPI 通知渠道收集跳过: {}", ex.getMessage());
        }
        log.info("[collect] 通知渠道注册完成 channels={}", channels.keySet());
    }

    @Override
    public List<CollectNotice> listNotices() {
        return list(Wrappers.<CollectNotice>lambdaQuery()
                .orderByDesc(CollectNotice::getCollectNoticeId));
    }

    @Override
    public List<CollectNotifyChannelVO> channelOptions() {
        List<CollectNotifyChannelVO> options = new ArrayList<>();
        for (CollectNotifyChannel channel : channels.values()) {
            options.add(new CollectNotifyChannelVO(channel.channel(), channel.label(), channel.targetHint()));
        }
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectNotice addNotice(CollectNotice notice) {
        check(notice);
        notice.setCollectNoticeId(null);
        save(notice);
        return notice;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean editNotice(CollectNotice notice) {
        requireNotice(notice.getCollectNoticeId());
        check(notice);
        return updateById(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNotice(Integer noticeId) {
        requireNotice(noticeId);
        return removeById(noticeId);
    }

    @Override
    public MessageResponse testNotice(Integer noticeId) {
        if (!properties.getNotify().isEnable()) {
            throw new IllegalArgumentException("执行通知已关闭（plugin.collect.notify.enable=false）");
        }
        CollectNotice notice = requireNotice(noticeId);
        CollectNotifyChannel channel = channels.get(notice.getCollectNoticeChannel());
        if (channel == null) {
            return MessageResponse.failure("未注册的渠道: " + notice.getCollectNoticeChannel()
                    + "，可用: " + channels.keySet());
        }
        Map<String, Object> vars = testVariables(notice);
        MessageResponse response = channel.send(buildMessage(notice, vars));
        markResult(notice.getCollectNoticeId(), "试发 " + describe(response));
        return response;
    }

    @Override
    public void notifyTask(CollectTask task, CollectRunResult result) {
        try {
            if (!properties.getNotify().isEnable()) {
                return;
            }
            if (CollectTriggerType.TEST.equals(task.getCollectTaskTriggerType())) {
                return;
            }
            String status = result.getStatus();
            List<CollectNotice> matched = list(Wrappers.<CollectNotice>lambdaQuery()
                    .eq(CollectNotice::getCollectNoticeEnabled, ENABLED));
            List<CollectNotice> targets = new ArrayList<>();
            for (CollectNotice notice : matched) {
                if (CollectNotifyTiming.matches(notice.getCollectNoticeTiming(), status)) {
                    targets.add(notice);
                }
            }
            if (targets.isEmpty()) {
                return;
            }
            Map<String, Object> vars = taskVariables(task, result);
            for (CollectNotice notice : targets) {
                sender.execute(() -> deliver(notice, vars));
            }
        } catch (Exception ex) {
            log.warn("[collect] 通知投递编排失败 task={} err={}", task.getCollectTaskNo(), ex.getMessage());
        }
    }

    /**
     * 关闭发送线程，给在途通知最多 3 秒。
     */
    @PreDestroy
    public void shutdown() {
        sender.shutdown();
        try {
            if (!sender.awaitTermination(3L, TimeUnit.SECONDS)) {
                sender.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            sender.shutdownNow();
        }
    }

    /**
     * 投递一条通知并回写最近结果，任何异常都在这里收口。
     *
     * @param notice 通知配置
     * @param vars   模板变量
     */
    private void deliver(CollectNotice notice, Map<String, Object> vars) {
        String label;
        try {
            CollectNotifyChannel channel = channels.get(notice.getCollectNoticeChannel());
            if (channel == null) {
                label = "未注册的渠道: " + notice.getCollectNoticeChannel();
            } else {
                MessageResponse response = channel.send(buildMessage(notice, vars));
                label = describe(response);
            }
        } catch (Exception ex) {
            log.warn("[collect] 通知发送异常 notice={} err={}", notice.getCollectNoticeId(), ex.toString());
            label = "发送异常: " + ex.getMessage();
        }
        markResult(notice.getCollectNoticeId(), label);
    }

    /**
     * 渲染模板并组装发送入参。
     *
     * @param notice 通知配置
     * @param vars   模板变量
     * @return 发送入参
     */
    private CollectNotifyMessage buildMessage(CollectNotice notice, Map<String, Object> vars) {
        String subject = notice.getCollectNoticeSubject();
        String content = notice.getCollectNoticeContent();
        return new CollectNotifyMessage(
                notice.getCollectNoticeTarget(),
                CollectExpression.evalText(blankToDefault(subject, DEFAULT_SUBJECT), vars),
                CollectExpression.evalText(blankToDefault(content, DEFAULT_CONTENT), vars),
                "html",
                properties.getRequestTimeoutMs());
    }

    /**
     * 回写最近一次发送结果。
     *
     * @param noticeId 通知ID
     * @param result   结果说明
     */
    private void markResult(Integer noticeId, String result) {
        CollectNotice update = new CollectNotice();
        update.setCollectNoticeId(noticeId);
        update.setCollectNoticeLastResult(cut(result, RESULT_LIMIT));
        update.setCollectNoticeLastTime(LocalDateTime.now());
        try {
            updateById(update);
        } catch (Exception ex) {
            log.warn("[collect] 通知结果回写失败 notice={} err={}", noticeId, ex.getMessage());
        }
    }

    /**
     * 任务终态时的模板变量。
     *
     * @param task   任务行
     * @param result 执行结果
     * @return 模板变量
     */
    private Map<String, Object> taskVariables(CollectTask task, CollectRunResult result) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("flowId", task.getCollectTaskFlowId());
        vars.put("flowName", blankToDefault(task.getCollectTaskFlowName(), "未命名流程"));
        vars.put("taskId", task.getCollectTaskId());
        vars.put("taskNo", blankToDefault(task.getCollectTaskNo(), ""));
        vars.put("status", blankToDefault(result.getStatus(), ""));
        vars.put("statusText", statusText(result.getStatus()));
        vars.put("trigger", blankToDefault(task.getCollectTaskTriggerType(), ""));
        vars.put("cost", result.getCostMs());
        vars.put("nodeCount", result.getNodeCount());
        vars.put("requestCount", result.getRequestCount());
        vars.put("recordCount", result.getRecordCount());
        vars.put("error", blankToDefault(result.getErrorMessage(), "无"));
        vars.put("beginTime", formatTime(task.getCollectTaskBeginTime()));
        vars.put("endTime", formatTime(LocalDateTime.now()));
        vars.put("executorId", blankToDefault(task.getCollectTaskExecutorId(), ""));
        return vars;
    }

    /**
     * 试发时的模板变量，不带真实任务。
     *
     * @param notice 通知配置
     * @return 模板变量
     */
    private Map<String, Object> testVariables(CollectNotice notice) {
        Map<String, Object> vars = new LinkedHashMap<>();
        // 试发没有真实任务，flowId 只能给 0 占位，避免模板里引用 ${flowId} 时渲染成字面量 null
        vars.put("flowId", 0);
        vars.put("flowName", blankToDefault(notice.getCollectNoticeName(), "通知试发"));
        vars.put("taskId", 0L);
        vars.put("taskNo", "TEST" + System.currentTimeMillis());
        vars.put("status", "TEST");
        vars.put("statusText", "试发");
        vars.put("trigger", "MANUAL");
        vars.put("cost", 0L);
        vars.put("nodeCount", 0);
        vars.put("requestCount", 0);
        vars.put("recordCount", 0);
        vars.put("error", "无");
        vars.put("beginTime", formatTime(LocalDateTime.now()));
        vars.put("endTime", formatTime(LocalDateTime.now()));
        vars.put("executorId", "console");
        return vars;
    }

    /**
     * 注册渠道。
     *
     * @param channel 渠道实现
     */
    private void register(CollectNotifyChannel channel) {
        if (channel == null || channel.channel() == null || channel.channel().isBlank()) {
            return;
        }
        CollectNotifyChannel previous = channels.putIfAbsent(channel.channel().trim(), channel);
        if (previous != null && previous != channel) {
            log.warn("[collect] 通知渠道重复注册，已忽略 channel={} 由 {} 提供",
                    channel.channel(), previous.getClass().getSimpleName());
        }
    }

    /**
     * 校验通知配置。
     *
     * @param notice 通知配置
     */
    private void check(CollectNotice notice) {
        if (notice.getCollectNoticeEnabled() == null) {
            notice.setCollectNoticeEnabled(ENABLED);
        }
        if (notice.getCollectNoticeTiming() == null || notice.getCollectNoticeTiming().isBlank()) {
            notice.setCollectNoticeTiming(CollectNotifyTiming.FAILURE);
        } else if (!CollectNotifyTiming.ALWAYS.equals(notice.getCollectNoticeTiming())
                && !CollectNotifyTiming.SUCCESS.equals(notice.getCollectNoticeTiming())
                && !CollectNotifyTiming.FAILURE.equals(notice.getCollectNoticeTiming())) {
            throw new IllegalArgumentException("触发时机只能是 SUCCESS/FAILURE/ALWAYS: "
                    + notice.getCollectNoticeTiming());
        }
        if (notice.getCollectNoticeChannel() == null || notice.getCollectNoticeChannel().isBlank()) {
            throw new IllegalArgumentException("请选择通知渠道");
        }
        notice.setCollectNoticeChannel(notice.getCollectNoticeChannel().trim());
        if (!channels.containsKey(notice.getCollectNoticeChannel())) {
            throw new IllegalArgumentException("未注册的渠道: " + notice.getCollectNoticeChannel()
                    + "，可用: " + channels.keySet());
        }
        if (notice.getCollectNoticeTarget() == null || notice.getCollectNoticeTarget().isBlank()) {
            throw new IllegalArgumentException("接收目标不能为空");
        }
        notice.setCollectNoticeTarget(notice.getCollectNoticeTarget().trim());
    }

    /**
     * 按主键取通知配置。
     *
     * @param noticeId 通知ID
     * @return 通知配置
     */
    private CollectNotice requireNotice(Integer noticeId) {
        if (noticeId == null) {
            throw new IllegalArgumentException("通知ID不能为空");
        }
        CollectNotice notice = getById(noticeId);
        if (notice == null) {
            throw new IllegalArgumentException("通知配置不存在: " + noticeId);
        }
        return notice;
    }

    /**
     * 发送结果转一行说明。
     *
     * @param response 发送结果
     * @return 说明文本
     */
    private String describe(MessageResponse response) {
        if (response == null) {
            return "渠道无响应";
        }
        if (response.isSuccess()) {
            return "成功 " + response.getDurationMillis() + "ms"
                    + (response.getMessageId() == null ? "" : " " + response.getMessageId());
        }
        return "失败: " + response.getErrorMessage();
    }

    /**
     * 任务状态中文。
     *
     * @param status 状态
     * @return 中文说明
     */
    private String statusText(String status) {
        if (CollectTaskStatus.SUCCESS.equals(status)) {
            return "成功";
        }
        if (CollectTaskStatus.FAILURE.equals(status)) {
            return "失败";
        }
        if (CollectTaskStatus.STOPPED.equals(status)) {
            return "已停止";
        }
        if (CollectTaskStatus.SKIPPED.equals(status)) {
            return "已跳过";
        }
        return blankToDefault(status, "未知");
    }

    /**
     * 格式化时间。
     *
     * @param time 时间，可为空
     * @return 文本，空返回"-"
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : TIME_FORMAT.format(time);
    }

    /**
     * 空值转默认值。
     *
     * @param value      原值
     * @param defaultVal 默认值
     * @return 非空文本
     */
    private String blankToDefault(String value, String defaultVal) {
        return value == null || value.isBlank() ? defaultVal : value;
    }

    /**
     * 截断超长文本。
     *
     * @param value 原值
     * @param limit 最大长度
     * @return 截断后的文本
     */
    private String cut(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }}