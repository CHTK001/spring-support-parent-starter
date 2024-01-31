package com.chua.starter.monitor.server.request;

import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.encryption.BootRequestDecode;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.Hex;
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
        BootRequestDecode decode = ServiceProvider.of(BootRequestDecode.class).getNewExtension(monitorServerProperties.getEncryptionSchema(), monitorServerProperties.getEncryptionKey());
        try {
            return decode.decode(Hex.decodeHex(data));
        } catch (Exception e) {
            return null;
        }
    }
}
