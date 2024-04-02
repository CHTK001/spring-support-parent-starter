package com.chua.starter.monitor.server.request;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.spi.ServiceProvider;
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
     * @return {@link BootRequest}
     */
    public BootRequest getRequest(MonitorServerProperties monitorServerProperties) {
        Codec decode = ServiceProvider.of(Codec.class)
                .getNewExtension(monitorServerProperties.getEncryptionSchema(), monitorServerProperties.getEncryptionKey());
        try {
            return BootRequest.builder().content(StringUtils.utf8Str(decode.decode(Hex.decodeHex(data)))).build();
        } catch (Exception e) {
            return null;
        }
    }
}
