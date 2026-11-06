package com.chua.starter.collect.notify.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.task.message.MessageResponse;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.collect.dto.CollectNotifyMessage;
import com.chua.starter.collect.notify.CollectNotifyChannel;
import com.chua.starter.entity.service.SysMessage;
import com.chua.starter.system.service.SysMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 站内消息通知渠道：把执行结果投到登录用户自己的消息中心。
 *
 * <p>复用平台既有的消息中心（{@code common-system-starter} 的
 * {@link SysMessageService#broadcast}）而不是自己再写一套推送：走同一条链路，
 * 落的是 {@code sys_message} 表，登录后在「消息中心」里就能看到，和未读数角标联动
 * （{@code pageForUser} / {@code countUnread} / {@code markRead} 读的都是这张表）。</p>
 *
 * <h3>为什么不是 spring-support-message-starter</h3>
 * <p>消息模块与 {@code common-system-starter} 各自带了一整套 {@code SysMessage*}，
 * 4 个 Mapper 与 4 组 Service/Impl 简名相同，两者在同一个 Spring 上下文里
 * 会在扫描期抛 {@code ConflictingBeanDefinitionException}，应用根本起不来。
 * 本模块依赖链上本来就有 {@code common-system-starter}，用它不引任何新表、
 * 也不引入新依赖。消息模块的去向见
 * {@code com.chua.starter.message.support.controller.SysMessageController} 的类注释。</p>
 *
 * <h3>前置条件</h3>
 * <ul>
 *   <li>容器里要有 {@link SysMessageService}。本类带 {@code @ConditionalOnBean}，
 *       没有消息中心时不注册，前端渠道下拉里也不会出现这一项——优雅失败而不是启动报错。</li>
 *   <li>接收人是<b>用户 ID 数字</b>（{@code sys_message.sys_message_receiver_id}），
 *       不是登录账号。填错类型会在「最近结果」里看到明确的解析失败原因。</li>
 * </ul>
 *
 * <p>投递留痕落在 {@code sys_message} 本身，没有额外的中转表；
 * 单条通知只写一次库，不经过 Socket，所以不依赖任何推送服务是否启动。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(SysMessageService.class)
@ConditionalOnProperty(prefix = "plugin.collect.notify", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectStationNotifyChannel implements CollectNotifyChannel {

    /**
     * 渠道标识。
     */
    private static final String CHANNEL = "station";

    /**
     * 写入 {@code sys_message_channel} 的渠道值，与平台消息中心既有枚举一致。
     */
    private static final String MESSAGE_CHANNEL = "PLATFORM";

    /**
     * 消息类型：系统通知。
     */
    private static final String MESSAGE_TYPE = "system";

    /**
     * 消息级别：普通。执行通知不是告警，级别由通知配置自身的语义决定。
     */
    private static final String MESSAGE_LEVEL = "normal";

    /**
     * 接收人分隔正则：半角/全角逗号与分号、连续空白都算。
     */
    private static final String TARGET_SEPARATORS = "[,，;；\\s]+";

    /**
     * 消息中心服务。用 ObjectProvider 承接是双保险：{@code @ConditionalOnBean}
     * 已经拦掉了没有该依赖的容器，这里再兜一层，避免条件判定与实际注册时序不一致时取到 null。
     */
    private final ObjectProvider<SysMessageService> messageServiceProvider;

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public String label() {
        return "站内消息";
    }

    @Override
    public String targetHint() {
        return "接收人用户 ID，多个用逗号分隔";
    }

    @Override
    public MessageResponse send(CollectNotifyMessage message) {
        long start = System.currentTimeMillis();
        SysMessageService service = messageServiceProvider.getIfAvailable();
        if (service == null) {
            return MessageResponse.failure("未接入平台消息中心（缺少 common-system-starter 依赖）");
        }
        var targets = parseTargets(message.target());
        if (targets.ids().isEmpty()) {
            return MessageResponse.failure(targets.invalid().isEmpty()
                    ? "站内消息接收人不能为空，请填写用户 ID"
                    : "接收人不是合法的用户 ID: " + String.join("、", targets.invalid()));
        }

        SysMessage entity = new SysMessage();
        entity.setSysMessageTitle(message.subject());
        entity.setSysMessageContent(message.content());
        entity.setSysMessageType(MESSAGE_TYPE);
        entity.setSysMessageLevel(MESSAGE_LEVEL);
        entity.setSysMessageChannel(MESSAGE_CHANNEL);
        entity.setSysMessageSenderId(0);
        entity.setSysMessageSenderName("采集调度");

        ReturnResult<Integer> result;
        try {
            result = service.broadcast(entity, targets.ids());
        } catch (Exception ex) {
            log.warn("[collect] 站内消息写入失败 err={}", ex.getMessage());
            return MessageResponse.failure("写入消息中心失败: " + ex.getMessage());
        }
        long cost = System.currentTimeMillis() - start;
        if (result == null || !result.isOk()) {
            return MessageResponse.builder()
                    .success(false)
                    .errorMessage(result == null ? "消息中心没有返回结果" : result.getMsg())
                    .durationMillis(cost)
                    .build();
        }
        return MessageResponse.builder()
                .success(true)
                .messageId(summarize(result.getData(), targets.invalid()))
                .durationMillis(cost)
                .build();
    }

    /**
     * 解析接收人为用户 ID 列表。
     *
     * <p>逐项解析而不是整段 {@code parseInt}：接收人里混进一个非法值时，
     * 整段失败会让一个手误把整批通知全部丢掉。合法项照常投递，非法项原样带回，
     * 在「最近结果」里告诉人哪几项没生效。</p>
     *
     * @param target 原始接收人文本，可为空
     * @return 合法用户 ID 与被跳过的原始片段
     */
    private Targets parseTargets(String target) {
        List<Integer> ids = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        if (StringUtils.isBlank(target)) {
            return new Targets(ids, invalid);
        }
        for (String item : Arrays.stream(target.split(TARGET_SEPARATORS))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toList()) {
            try {
                ids.add(Integer.valueOf(item));
            } catch (NumberFormatException ex) {
                log.warn("[collect] 站内消息接收人不是用户 ID 已跳过: {}", item);
                invalid.add(item);
            }
        }
        return new Targets(ids, invalid);
    }

    /**
     * 汇总投递说明：有接收人被跳过时必须说出来，否则「已投递 3 人」会让人以为
     * 配 4 个人也都收到了。
     *
     * @param count   消息中心实际写入条数
     * @param invalid 被跳过的接收人片段
     * @return 投递说明
     */
    private String summarize(int count, List<String> invalid) {
        if (invalid.isEmpty()) {
            return "已投递 " + count + " 人";
        }
        return "已投递 " + count + " 人，另有 " + invalid.size() + " 个接收人不是用户 ID 已跳过: "
                + String.join("、", invalid);
    }

    /**
     * 接收人解析结果。
     *
     * @param ids     合法用户 ID
     * @param invalid 被跳过的原始片段
     */
    private record Targets(List<Integer> ids, List<String> invalid) {
    }
}
