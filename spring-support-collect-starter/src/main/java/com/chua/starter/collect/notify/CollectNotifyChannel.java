package com.chua.starter.collect.notify;

import com.chua.common.support.task.message.MessageResponse;
import com.chua.starter.collect.dto.CollectNotifyMessage;

/**
 * 执行通知渠道扩展点。
 *
 * <p>实现类以 Spring Bean 或 {@code @Spi("渠道名")} 提供，注册后直接出现在通知配置的渠道下拉里，
 * 新增渠道不需要改发送方与前端。渠道只负责把一条已渲染好的消息投递出去，
 * 模板渲染、命中判断与结果回写都在通知服务里完成。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectNotifyChannel {

    /**
     * 渠道标识，与通知配置里的渠道字段对应。
     *
     * @return 渠道标识，全局唯一
     */
    String channel();

    /**
     * 渠道显示名，用于前端下拉与结果提示。
     *
     * @return 显示名
     */
    default String label() {
        return channel();
    }

    /**
     * 该渠道需要填什么，作为前端输入框的提示。
     *
     * @return 目标填写说明
     */
    default String targetHint() {
        return "接收目标";
    }

    /**
     * 发送通知。
     *
     * <p>失败请返回 {@link MessageResponse#failure(String)}，不要抛异常给调用方以外的地方；
     * 发送方仍会捕获兜底，但返回值会写进通知记录的最近结果里供排查。</p>
     *
     * @param message 已渲染的消息
     * @return 发送结果
     */
    MessageResponse send(CollectNotifyMessage message);
}
