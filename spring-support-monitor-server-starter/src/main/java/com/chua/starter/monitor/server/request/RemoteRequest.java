package com.chua.starter.monitor.server.request;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.utils.Hex;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.properties.MonitorServerProperties;
import lombok.Data;

/**
 * @author CH
 */
@Data
public class RemoteRequest {

    private String data;

    /**
     * 收到请求
     *
     * @param monitorServerProperties 统一服务器属性
     * @return {@link Request}
     */
    public Request getRequest(MonitorServerProperties monitorServerProperties) {
        Codec decode = Codec.build(monitorServerProperties.getEncryptionSchema(), monitorServerProperties.getEncryptionKey());
        try {
            return Json.fromJson(StringUtils.utf8Str(decode.decode(Hex.decodeHex(data))), Request.class);
        } catch (Exception e) {
            return null;
        }
    }
}
