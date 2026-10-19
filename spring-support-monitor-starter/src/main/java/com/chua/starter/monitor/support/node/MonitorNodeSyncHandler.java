package com.chua.starter.monitor.support.node;

import com.chua.common.support.network.sync.SyncMessageHandler;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * report 客户端应用信息上报处理器。
 *
 * <p>把 {@code monitor/app/report} 的消息交给 {@link MonitorNodeRegistry}，
 * 与 {@code sync/client/register} 的注册信息按会话标识合并。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RequiredArgsConstructor
public class MonitorNodeSyncHandler implements SyncMessageHandler {

    private final MonitorNodeRegistry registry;

    @Override
    public void handle(String topic, Object message) {
        if (!(message instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        raw.forEach((key, value) -> data.put(String.valueOf(key), value));
        registry.onAppReport(data);
    }
}
