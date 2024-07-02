package com.chua.starter.monitor.server.request;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.properties.MonitorServerProperties;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * @author CH
 */
@Data
@Component
public class RemoteRequest {

    private Codec codec;
    public RemoteRequest(MonitorServerProperties monitorServerProperties) {
        codec = getCodec(monitorServerProperties);
    }


    public Codec getCodec(MonitorServerProperties monitorServerProperties) {
        return Codec.build(monitorServerProperties.getEncryptionSchema(), monitorServerProperties.getEncryptionKey());
    }
    /**
     * 收到请求
     *
     * @return {@link Request}
     */
    public ReportQuery getRequest(byte[] data) {
        try {
            return Json.fromJson(StringUtils.utf8Str(codec.decode(data)), ReportQuery.class);
        } catch (Exception e) {
            return null;
        }
    }
}
