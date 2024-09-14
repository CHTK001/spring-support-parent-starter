package com.chua.report.client.starter.service;

import com.chua.common.support.json.Json;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.setting.SettingFactory;
import io.zbus.mq.Message;
import io.zbus.mq.Producer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;

/**
 * report服务
 *
 * @author CH
 * @since 2024/9/13
 */
@Slf4j
public class ReportService {

    private static final ExecutorService EXECUTOR_SERVICE = ThreadUtils.newVirtualThreadExecutor();

    private final SettingFactory settingFactory = SettingFactory.getInstance();

    /**
     * 发送请求
     *
     * @param value 参数
     */
    public <T> void report(ReportEvent<T> value) {
        if (settingFactory.isServer() || !settingFactory.isEnable()) {
            return;
        }
        Producer reportProducer = settingFactory.getReportProducer();
        if (null == reportProducer) {
            return;
        }

        EXECUTOR_SERVICE.execute(() -> {
            Message message = new Message();
            message.setBody(Json.toJSONBytes(value));
            message.setTopic(settingFactory.getReportTopic());
            try {
                reportProducer.publish(message);
            } catch (Exception ignored) {
            }
        });
    }

}
