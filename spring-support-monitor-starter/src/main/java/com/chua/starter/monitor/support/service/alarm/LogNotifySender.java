package com.chua.starter.monitor.support.service.alarm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
* 日志通知发送器（兜底渠道）
* <p>
* 始终生效：保证告警事件至少有可观测出口；其他渠道（webhook 等）
* 作为额外 Bean 并行投递。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
public class LogNotifySender implements NotifySender {

    /**
    * 渠道名称
    *
    * @return 渠道标识
     */
    @Override
    public String channel() {
        return "log";
    }

    /**
    * 以 WARN 级别输出告警内容
    *
    * @param title    标题
    * @param content  内容
    * @param eventUrl 事件详情地址
     */
    @Override
    public void send(String title, String content, String eventUrl) {
        log.warn("[alarm] {} | {} | {}", title, content, eventUrl == null ? "-" : eventUrl);
    }
}
