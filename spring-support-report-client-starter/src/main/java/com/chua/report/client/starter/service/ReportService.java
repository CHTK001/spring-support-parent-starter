package com.chua.report.client.starter.service;

import com.chua.common.support.json.Json;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.mica.support.client.session.MicaSession;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.setting.SettingFactory;
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
        MicaSession reportProducer = settingFactory.getReportProducer();
        if (null == reportProducer) {
            return;
        }

        ReportEvent.ReportType reportType = value.getReportType();
        if( !settingFactory.contains(reportType)) {
            return;
        }

        if(ClassUtils.isPresent("com.chua.attach.hotspot.support.Agent")) {
            if(reportType == ReportEvent.ReportType.LOG ||
                    reportType == ReportEvent.ReportType.SQL ||
                    reportType == ReportEvent.ReportType.TRACE) {
                return;
            }
        }

        EXECUTOR_SERVICE.execute(() -> {
            try {
                reportProducer.publish(settingFactory.getReportTopic(), Json.toJSONBytes(value));
            } catch (Exception ignored) {
            }
        });
    }

}
