package com.chua.starter.unified.server.support.entity;

import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.encryption.BootRequestDecode;
import com.chua.common.support.utils.Hex;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
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
     * @param unifiedServerProperties 统一服务器属性
     * @return {@link BootRequest}
     */
    public BootRequest getRequest(UnifiedServerProperties unifiedServerProperties) {
        BootRequestDecode decode = unifiedServerProperties.getDecode();
        try {
            return decode.decode(Hex.decodeHex(data));
        } catch (Exception e) {
            return null;
        }
    }
}
