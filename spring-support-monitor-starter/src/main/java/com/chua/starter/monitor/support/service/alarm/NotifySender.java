package com.chua.starter.monitor.support.service.alarm;

/**
 * 告警通知发送器 SPI
 * <p>
 * 告警事件真实触发后由引擎回调；实现方可对接钉钉/飞书/webhook 等渠道。
 * 通过 Spring Bean 收集全部实现，逐一投递，单个渠道失败不影响其他渠道。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface NotifySender {

    /**
     * 渠道名称（用于日志与扩展注册）
     *
     * @return 渠道标识
     */
    String channel();

    /**
     * 发送告警通知
     *
     * @param title    标题
     * @param content  内容（含目标/指标/实际值/阈值）
     * @param eventUrl 事件详情地址，可为空
     */
    void send(String title, String content, String eventUrl);
}
